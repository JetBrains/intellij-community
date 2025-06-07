// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import com.intellij.openapi.vcs.FilePath;

import java.util.Comparator;

public final class FilePathByPathComparator implements Comparator<FilePath> {
  private static final FilePathByPathComparator ourInstance = new FilePathByPathComparator();

  public static FilePathByPathComparator getInstance() {
    return ourInstance;
  }

  @Override
  public int compare(FilePath o1, FilePath o2) {
    return o1.getPath().compareTo(o2.getPath());
  }
}
