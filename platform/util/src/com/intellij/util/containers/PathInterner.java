/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.containers;

import com.intellij.util.io.IOUtil;
import gnu.trove.THashMap;
import gnu.trove.TObjectHashingStrategy;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author peter
 */
public class PathInterner {
  private static final TObjectHashingStrategy<SubstringWrapper[]> HASHING_STRATEGY = new TObjectHashingStrategy<SubstringWrapper[]>() {
    @Override
    public int computeHashCode(SubstringWrapper[] object) {
      return Arrays.hashCode(object);
    }

    @Override
    public boolean equals(SubstringWrapper[] o1, SubstringWrapper[] o2) {
      return Arrays.equals(o1, o2);
    }
  };
  private final OpenTHashSet<SubstringWrapper> myInternMap = new OpenTHashSet<SubstringWrapper>();

  @Nullable
  protected SubstringWrapper[] internParts(String path, boolean forAddition) {
    int start = 0;
    boolean asBytes = forAddition && IOUtil.isAscii(path);
    List<SubstringWrapper> key = new ArrayList<SubstringWrapper>();
    SubstringWrapper flyweightKey = new SubstringWrapper();
    while (start < path.length()) {
      flyweightKey.findSubStringUntilNextSeparator(path, start);
      SubstringWrapper interned = myInternMap.get(flyweightKey);
      if (interned == null) {
        if (!forAddition) {
          return null;
        }
        myInternMap.add(interned = flyweightKey.createPersistentCopy(asBytes));
      }
      key.add(interned);
      start += flyweightKey.len;
    }
    return key.toArray(new SubstringWrapper[key.size()]);
  }

  private static String restorePath(SubstringWrapper[] seq) {
    StringBuilder sb = new StringBuilder();
    for (SubstringWrapper wrapper : seq) {
      wrapper.append(sb);
    }
    return sb.toString();
  }

  private static class SubstringWrapper {
    private Object encodedString;
    private int start;
    private int len;
    private int hc;

    void append(StringBuilder sb) {
      if (encodedString instanceof String) {
        sb.append(encodedString);
        return;
      }

      int oldLen = sb.length();
      sb.setLength(oldLen + len);
      byte[] bytes = (byte[]) encodedString;
      //noinspection ForLoopReplaceableByForEach
      for (int i = 0, len = bytes.length; i < len; i++) {
        sb.setCharAt(oldLen + i, (char)bytes[i]);
      }
    }

    void findSubStringUntilNextSeparator(String s, int start) {
      encodedString = s;
      this.start = start;
      hc = 0;

      while (start < s.length() && isSeparator(s.charAt(start))) {
        hc = hc * 31 + s.charAt(start);
        start++;
      }
      while (start < s.length() && !isSeparator(s.charAt(start))) {
        hc = hc * 31 + s.charAt(start);
        start++;
      }
      this.len = start - this.start;
    }

    private static boolean isSeparator(char c) {
      return c == '/' || c == '\\' || c == '.' || c == ' ' || c == '_' || c == '$';
    }

    char charAt(int i) {
      if (encodedString instanceof String) {
        return ((String)encodedString).charAt(start + i);
      }
      return (char)((byte[]) encodedString)[start + i];
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof SubstringWrapper)) return false;

      SubstringWrapper wrapper = (SubstringWrapper)o;

      if (hc != wrapper.hc) return false;
      if (len != wrapper.len) return false;

      for (int i = 0; i < len; i++) {
        if (charAt(i) != wrapper.charAt(i)) {
          return false;
        }
      }

      return true;
    }

    @Override
    public int hashCode() {
      return hc;
    }

    SubstringWrapper createPersistentCopy(boolean asBytes) {
      SubstringWrapper wrapper = new SubstringWrapper();
      String string = (String) encodedString;
      String substring = string.substring(start, start + len);
      if (asBytes) {
        byte[] bytes = new byte[len];
        for (int i = 0; i < len; i++) {
          bytes[i] = (byte)string.charAt(i + start);
        }
        wrapper.encodedString = bytes;
      } else {
        wrapper.encodedString = new String(string.substring(start, start + len));
      }
      wrapper.start = 0;
      wrapper.len = len;
      wrapper.hc = hc;
      return wrapper;
    }
  }

  public static class PathEnumerator {
    private final TObjectIntHashMap<SubstringWrapper[]> mySeqToIdx = new TObjectIntHashMap<SubstringWrapper[]>(
      PathInterner.HASHING_STRATEGY);
    private final List<SubstringWrapper[]> myIdxToSeq = new ArrayList<SubstringWrapper[]>();
    private final PathInterner myInterner = new PathInterner();

    public PathEnumerator() {
      myIdxToSeq.add(null);
    }

    public List<String> getAllPaths() {
      ArrayList<String> result = new ArrayList<String>(myIdxToSeq.size() - 1);
      for (SubstringWrapper[] wrappers : myIdxToSeq) {
        if (wrappers != null) {
          result.add(PathInterner.restorePath(wrappers));
        }
      }
      return result;
    }

    public int addPath(String path) {
      PathInterner.SubstringWrapper[] seq = myInterner.internParts(path, true);
      if (!mySeqToIdx.containsKey(seq)) {
        mySeqToIdx.put(seq, myIdxToSeq.size());
        myIdxToSeq.add(seq);
      }
      return mySeqToIdx.get(seq);
    }

    public String retrievePath(int idx) {
      try {
        return PathInterner.restorePath(myIdxToSeq.get(idx));
      }
      catch (IndexOutOfBoundsException e) {
        throw new IllegalArgumentException("Illegal index: " + idx);
      }
    }

    public int getExistingPathIndex(String path) {
      PathInterner.SubstringWrapper[] key = myInterner.internParts(path, false);
      return key != null && mySeqToIdx.containsKey(key) ? mySeqToIdx.get(key) : 0;
    }
    public boolean containsPath(String path) {
      PathInterner.SubstringWrapper[] key = myInterner.internParts(path, false);
      return key != null && mySeqToIdx.containsKey(key);
    }
  }

  public static class PathMap<T> {
    private final THashMap<SubstringWrapper[], T> myMap = new THashMap<SubstringWrapper[], T>(PathInterner.HASHING_STRATEGY);
    private final PathInterner myInterner = new PathInterner();

    @Nullable
    public T get(@NotNull String path) {
      PathInterner.SubstringWrapper[] seq = myInterner.internParts(path, false);
      return seq == null ? null : myMap.get(seq);
    }

    public void put(@NotNull String path, @NotNull T value) {
      myMap.put(myInterner.internParts(path, true), value);
    }


    public Iterable<T> values() {
      return myMap.values();
    }
  }

}
