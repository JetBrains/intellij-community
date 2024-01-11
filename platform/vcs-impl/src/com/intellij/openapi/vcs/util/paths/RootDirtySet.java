// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.util.paths;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcsUtil.VcsUtil;
import it.unimi.dsi.fastutil.ints.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class RootDirtySet {
  private static final Logger LOG = Logger.getInstance(RootDirtySet.class);

  private static final int FOLDER_SIZE_THRESHOLD = 30;
  private static final int DIRTY_SCOPE_SIZE_THRESHOLD = 50000;

  private final @NotNull @NonNls String myRoot;
  private final boolean myCaseSensitive;

  private final Set<@NonNls String> myPaths;
  private final IntSet myPathHashSet = new IntOpenHashSet();
  private final Int2IntMap myPathHashCounters = new Int2IntOpenHashMap();
  private boolean myEverythingDirty;

  public RootDirtySet(@NotNull FilePath root, boolean caseSensitive) {
    this(root.getPath(), caseSensitive);
  }

  private RootDirtySet(@NotNull String root, boolean caseSensitive) {
    myRoot = root;
    myCaseSensitive = caseSensitive;

    myPaths = caseSensitive ? new HashSet<>() : CollectionFactory.createCaseInsensitiveStringSet();
  }

  public void markDirty(@NotNull FilePath filePath) {
    if (myEverythingDirty) return;

    String path = filePath.getPath();
    if (getParentPrefixOf(path, myRoot, myCaseSensitive) != -1) {
      markEverythingDirty();
      return;
    }

    int startIndex = getParentPrefixOf(myRoot, path, myCaseSensitive);
    if (startIndex == -1) {
      LOG.error(new Throwable(String.format("Invalid dirty path for root %s: %s", myRoot, path)));
      return;
    }

    markDirtyRelative(path, startIndex);
  }

  private void markDirtyRelative(@NotNull String path, int startIndex) {
    if (myPaths.size() > DIRTY_SCOPE_SIZE_THRESHOLD) {
      // Avoid performance issues for specific 'poorly mergeable' dirty path configurations.
      // This should not happen in practice.
      markEverythingDirty();
      return;
    }

    int index = startIndex;
    int previousPrefixHash = 0;

    IntList prefixHashes = new IntArrayList();
    prefixHashes.add(0);

    int prevCounter = myPathHashCounters.get(0);
    boolean needCutOff = false;

    while (index < path.length()) {
      int nextIndex = path.indexOf('/', index + 1);
      if (nextIndex == -1) nextIndex = path.length();

      int prefixHash = FilePathHashUtil.pathHashCode(myCaseSensitive, path, index, nextIndex, previousPrefixHash);

      if (myPathHashSet.contains(prefixHash)) {
        String prefix = path.substring(startIndex, nextIndex);
        if (myPaths.contains(prefix)) return;
      }

      int counter = myPathHashCounters.get(prefixHash);

      boolean addParentFolder = needCutOff && counter < prevCounter ||
                                prevCounter - counter > FOLDER_SIZE_THRESHOLD;

      if (counter < FOLDER_SIZE_THRESHOLD && prevCounter >= FOLDER_SIZE_THRESHOLD) {
        needCutOff = true; // merge current folder
      }

      if (addParentFolder) {
        if (startIndex == index) {
          markEverythingDirty();
          return;
        }

        myPaths.add(path.substring(startIndex, index));
        myPathHashSet.add(previousPrefixHash);

        for (int i = 0; i < prefixHashes.size(); i++) {
          int hash = prefixHashes.getInt(i);
          myPathHashCounters.mergeInt(hash, 1 - prevCounter, (val1, val2) -> Math.max(val1 + val2, 1));
        }
        return;
      }

      // do not increase counter - handle hash collisions
      if (counter < prevCounter) {
        prevCounter = counter;
      }

      prefixHashes.add(prefixHash);
      previousPrefixHash = prefixHash;
      index = nextIndex;
    }

    myPaths.add(path.substring(startIndex, index));
    myPathHashSet.add(previousPrefixHash);

    for (int i = 0; i < prefixHashes.size(); i++) {
      int hash = prefixHashes.getInt(i);
      myPathHashCounters.mergeInt(hash, 1, (val1, val2) -> val1 + val2);
    }
  }

  public boolean belongsTo(@NotNull FilePath filePath) {
    String path = filePath.getPath();
    int startIndex = getParentPrefixOf(myRoot, path, myCaseSensitive);
    if (startIndex == -1) {
      return false;
    }

    if (myEverythingDirty) {
      return true;
    }
    if (path.length() == myRoot.length()) {
      return false; // myEverythingDirty == false
    }

    int index = startIndex;
    int lastPrefixHash = 0;

    while (index < path.length()) {
      int nextIndex = path.indexOf('/', index + 1);
      if (nextIndex == -1) nextIndex = path.length();

      int prefixHash = FilePathHashUtil.pathHashCode(myCaseSensitive, path, index, nextIndex, lastPrefixHash);

      if (myPathHashSet.contains(prefixHash)) {
        String prefix = path.substring(startIndex, nextIndex);
        if (myPaths.contains(prefix)) return true;
      }

      index = nextIndex;
      lastPrefixHash = prefixHash;
    }

    return false;
  }

  public void markEverythingDirty() {
    myPaths.clear();
    myPathHashSet.clear();
    myPathHashCounters.clear();
    myEverythingDirty = true;
  }

  public boolean isEmpty() {
    return !myEverythingDirty && myPathHashCounters.isEmpty();
  }

  public boolean isEverythingDirty() {
    return myEverythingDirty;
  }

  public @NotNull List<FilePath> collectFilePaths() {
    if (myEverythingDirty) {
      return Collections.singletonList(VcsUtil.getFilePath(myRoot, true));
    }
    List<String> result = removeCommonParents(myPaths, myCaseSensitive);
    return ContainerUtil.map(result, path -> VcsUtil.getFilePath(myRoot + "/" + path, true));
  }

  public @NotNull RootDirtySet copy() {
    RootDirtySet copy = new RootDirtySet(myRoot, myCaseSensitive);
    if (myEverythingDirty) {
      copy.markEverythingDirty();
    }
    else {
      for (String filePath : myPaths) {
        copy.markDirtyRelative(filePath, 0);
      }
    }
    return copy;
  }

  public @NotNull RootDirtySet compact() {
    RootDirtySet copy = new RootDirtySet(myRoot, myCaseSensitive);
    if (myEverythingDirty) {
      copy.markEverythingDirty();
    }
    else {
      for (String filePath : removeCommonParents(myPaths, myCaseSensitive)) {
        copy.markDirtyRelative(filePath, 0);
      }
    }
    return copy;
  }

  private static @NotNull List<String> removeCommonParents(@NotNull Collection<String> paths, boolean caseSensitive) {
    List<String> sortedPaths = new ArrayList<>(paths);
    sortedPaths.sort(null);

    String prevPath = null;
    Iterator<String> it = sortedPaths.iterator();
    while (it.hasNext()) {
      String path = it.next();
      if (prevPath != null && FileUtil.startsWith(path, prevPath, caseSensitive)) {
        it.remove();
      }
      else {
        prevPath = path;
      }
    }

    return sortedPaths;
  }

  private static int getParentPrefixOf(@NotNull String ancestor, @NotNull String path, boolean caseSensitive) {
    if (caseSensitive) {
      if (!path.startsWith(ancestor)) return -1;
    }
    else {
      if (!StringUtil.startsWithIgnoreCase(path, ancestor)) return -1;
    }

    if (ancestor.length() == path.length() ||
        path.charAt(ancestor.length()) == '/') {
      return ancestor.length() + 1;
    }

    // "/" and "C:/" roots
    if (!ancestor.isEmpty() && ancestor.charAt(ancestor.length() - 1) == '/') {
      return ancestor.length();
    }

    return -1;
  }
}
