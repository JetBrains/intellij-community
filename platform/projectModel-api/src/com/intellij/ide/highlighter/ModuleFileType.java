// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.highlighter;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.fileTypes.InternalFileType;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.projectModel.ProjectModelBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public final class ModuleFileType implements InternalFileType {
  public static final ModuleFileType INSTANCE = new ModuleFileType();

  @NonNls public static final String DEFAULT_EXTENSION = "iml";
  @NonNls public static final String DOT_DEFAULT_EXTENSION = ".iml";

  private ModuleFileType() {}

  @Override
  @NotNull
  public String getName() {
    return "IDEA_MODULE";
  }

  @Override
  @NotNull
  public String getDescription() {
    return ProjectModelBundle.message("filetype.description.idea.module");
  }

  @Override
  @NotNull
  public String getDefaultExtension() {
    return DEFAULT_EXTENSION;
  }

  @Override
  public Icon getIcon() {
    return AllIcons.Nodes.IdeaModule;
  }

  @Override
  public boolean isBinary() {
    return false;
  }

  @Override
  public boolean isReadOnly() {
    return true;
  }

  @Override
  public String getCharset(@NotNull VirtualFile file, final byte @NotNull [] content) {
    return CharsetToolkit.UTF8;
  }
}
