// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.emojipicker;

import org.jetbrains.annotations.NonNls;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class EmojiSearchIndex implements Serializable {
  @NonNls private final String myChars;
  private final int[] myData;
  private final int myRootNodeOffset, myTotalEmojiIndices;

  private EmojiSearchIndex(@NonNls String chars, int[] data, int rootNodeOffset, int totalEmojiIndices) {
    myChars = chars;
    myData = data;
    myRootNodeOffset = rootNodeOffset;
    myTotalEmojiIndices = totalEmojiIndices;
  }

  private int findNodeForPrefix(@NonNls String key, int prefixOffset, int nodeOffset) {
    if (prefixOffset == key.length()) return nodeOffset;
    int children = myData[nodeOffset + 2];
    for (int c = 0; c < children; c++) {
      int child = myData[nodeOffset + 3 + c];
      int childCharsFrom = myData[child], childCharsTo = myData[child + 1];
      int i, limit = Math.min(key.length() - prefixOffset, childCharsTo - childCharsFrom);
      for (i = 0; i < limit; i++) {
        if (key.charAt(prefixOffset + i) != myChars.charAt(childCharsFrom + i)) break;
      }
      if (i == childCharsTo - childCharsFrom) return findNodeForPrefix(key, prefixOffset + i, child);
      if (i == key.length() - prefixOffset) return child;
      if (i > 0) return -1;
    }
    return -1;
  }

  private void collectIdsFromSubtree(boolean[] idMap, int nodeOffset) {
    int children = myData[nodeOffset + 2];
    int ids = myData[nodeOffset + 3 + children];
    for (int i = 0; i < ids; i++) {
      int id = myData[nodeOffset + 4 + children + i];
      idMap[id] = true;
    }
    for (int i = 0; i < children; i++) {
      int child = myData[nodeOffset + 3 + i];
      collectIdsFromSubtree(idMap, child);
    }
  }

  public boolean lookupIds(boolean[] idMap, @NonNls String prefix) {
    if (idMap.length < myTotalEmojiIndices) {
      throw new IllegalArgumentException("Output array is too small: " + idMap.length +
                                         ", but there are " + myTotalEmojiIndices + "emoji indices");
    }
    int node = findNodeForPrefix(prefix, 0, myRootNodeOffset);
    if (node == -1) return false;
    Arrays.fill(idMap, false);
    collectIdsFromSubtree(idMap, node);
    return true;
  }

  public int getTotalEmojiIndices() {
    return myTotalEmojiIndices;
  }


  public static class PrefixTree {

    @NonNls private String myPrefix;
    private final List<PrefixTree> myChildren = new ArrayList<>();
    private final List<Integer> myValues = new ArrayList<>();

    private PrefixTree(@NonNls String prefix) { myPrefix = prefix; }

    public PrefixTree() { this(""); }

    public void add(@NonNls String key, int value) {
      if (key.isEmpty()) {
        myValues.add(value);
        return;
      }
      for (int c = 0; c < myChildren.size(); c++) {
        PrefixTree child = myChildren.get(c);
        int i, limit = Math.min(key.length(), child.myPrefix.length());
        for (i = 0; i < limit; i++) {
          if (key.charAt(i) != child.myPrefix.charAt(i)) break;
        }
        if (i == child.myPrefix.length()) {
          child.add(key.substring(limit), value);
          return;
        }
        else if (i > 0) {
          PrefixTree
            firstPart = new PrefixTree(key.substring(0, i));
          child.myPrefix = child.myPrefix.substring(i);
          firstPart.myChildren.add(child);
          myChildren.set(c, firstPart);
          firstPart.add(key.substring(i), value);
          return;
        }
      }
      PrefixTree newSubtree = new PrefixTree(key);
      newSubtree.myValues.add(value);
      myChildren.add(newSubtree);
    }

    public EmojiSearchIndex buildIndex() {
      BuildData data = new BuildData();
      data.myChars = new StringBuffer(2000000);
      data.myData = new int[4000000];
      int first = collectToIndex(data);
      return new EmojiSearchIndex(data.myChars.toString(), Arrays.copyOf(data.myData, data.myDataPointer), first, data.myMaxEmojiId + 1);
    }

    private int collectToIndex(BuildData data) {
      int[] childrenLinks = new int[myChildren.size()];
      for (int i = 0; i < childrenLinks.length; i++) childrenLinks[i] = myChildren.get(i).collectToIndex(data);
      int charsFrom = data.myChars.length();
      data.myChars.append(myPrefix);
      int index = data.myDataPointer;
      data.ensureDataCapacity(4 + childrenLinks.length + myValues.size());
      data.write(charsFrom).write(data.myChars.length()).write(childrenLinks.length);
      System.arraycopy(childrenLinks, 0, data.myData, data.myDataPointer, childrenLinks.length);
      data.myDataPointer += childrenLinks.length;
      data.write(myValues.size());
      for (int i : myValues) {
        if (i > data.myMaxEmojiId) data.myMaxEmojiId = i;
        data.write(i);
      }
      return index;
    }

    private static class BuildData {
      StringBuffer myChars;
      int[] myData;
      int myDataPointer, myMaxEmojiId = -1;

      private void ensureDataCapacity(int remaining) {
        if (myDataPointer + remaining > myData.length) myData = Arrays.copyOf(myData, myData.length * 2);
      }

      private BuildData write(int i) {
        myData[myDataPointer++] = i;
        return this;
      }
    }
  }
}
