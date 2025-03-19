// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.update;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.io.File;
import java.util.*;

/**
 * author: lesya
 */
@ApiStatus.Internal
public class GroupByPackages {
  private final Map<File, Collection<File>> myParentToChildrenMap = new HashMap<>();
  private final Collection<File> myRoots = new HashSet<>();

  public GroupByPackages(@NotNull Collection<? extends File> files) {
    for (File file : files) {
      process(file);
    }
    splitRoots();
  }

  private void splitRoots() {
    for (File oldRoot : new ArrayList<>(myRoots)) {
      File newRoot = splitRoot(oldRoot);
      if (!oldRoot.equals(newRoot)) replaceRoot(oldRoot, newRoot);
    }
  }

  private void replaceRoot(File oldRoot, File newRoot) {
    myRoots.remove(oldRoot);
    myRoots.add(newRoot);
  }

  private File splitRoot(@NotNull File oldRoot) {
    List<File> children = getChildren(oldRoot);
    if (children.size() == 1) {
      return splitRoot(children.get(0));
    }
    return oldRoot;
  }

  private void process(final @NotNull File file) {
    File f;
    File parent = file.getParentFile();
    for (f = file; parent != null; f = parent, parent = parent.getParentFile()) {
      Collection<File> files = myParentToChildrenMap.get(parent);
      if (files == null) {
        myParentToChildrenMap.put(parent, files = new HashSet<>());
      }
      files.add(f);
    }
    myRoots.add(f);
  }

  public @NotNull List<File> getRoots() {
    return new ArrayList<>(myRoots);
  }

  public @NotNull @Unmodifiable List<File> getChildren(File file) {
    Collection<File> collection = myParentToChildrenMap.get(file);
    if (collection == null) {
      return ContainerUtil.emptyList();
    }
    return new ArrayList<>(collection);
  }
}
