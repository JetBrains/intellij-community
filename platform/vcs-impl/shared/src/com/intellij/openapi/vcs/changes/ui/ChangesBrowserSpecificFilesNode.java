// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

import static com.intellij.util.containers.ContainerUtil.count;

public class ChangesBrowserSpecificFilesNode<T> extends ChangesBrowserSpecificNode<T, VirtualFile> {
  protected ChangesBrowserSpecificFilesNode(@NotNull T userObject,
                                            @NotNull Collection<VirtualFile> files,
                                            @NotNull Runnable shower) {
    super(userObject, files, count(files, it -> it.isDirectory()), shower);
  }
}
