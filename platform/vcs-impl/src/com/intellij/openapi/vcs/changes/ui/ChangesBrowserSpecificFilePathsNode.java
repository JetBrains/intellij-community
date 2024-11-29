// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.vcs.FilePath;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

import static com.intellij.util.containers.ContainerUtil.count;

public class ChangesBrowserSpecificFilePathsNode<T> extends ChangesBrowserSpecificNode<T, FilePath> {
  protected ChangesBrowserSpecificFilePathsNode(@NotNull T userObject, @NotNull Collection<FilePath> files, @NotNull Runnable shower) {
    super(userObject,  files, count(files, it -> it.isDirectory()), shower);
  }
}
