// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileTypes.impl;

import com.intellij.ide.highlighter.ModuleFileType;
import com.intellij.ide.highlighter.ProjectFileType;
import com.intellij.ide.highlighter.WorkspaceFileType;
import com.intellij.openapi.fileTypes.FileTypeConsumer;
import com.intellij.openapi.fileTypes.FileTypeFactory;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class InternalFileTypeFactory extends FileTypeFactory {
  @Override
  public void createFileTypes(@NotNull final FileTypeConsumer consumer) {
    consumer.consume(WorkspaceFileType.INSTANCE, WorkspaceFileType.DEFAULT_EXTENSION);
    consumer.consume(ModuleFileType.INSTANCE, ModuleFileType.DEFAULT_EXTENSION);
    consumer.consume(ProjectFileType.INSTANCE, ProjectFileType.DEFAULT_EXTENSION);
  }
}
