// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.highlighter;

import com.intellij.openapi.fileTypes.InternalFileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.projectModel.ProjectModelBundle;
import com.intellij.ui.IconManager;
import com.intellij.ui.PlatformIcons;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.nio.charset.StandardCharsets;

public final class ProjectFileType implements InternalFileType {
  public static final ProjectFileType INSTANCE = new ProjectFileType();

  public static final String DEFAULT_EXTENSION = "ipr";
  public static final String DOT_DEFAULT_EXTENSION = ".ipr";

  private ProjectFileType() { }

  @Override
  public @NotNull String getName() {
    return "IDEA_PROJECT";
  }

  @Override
  public @NotNull String getDescription() {
    return ProjectModelBundle.message("filetype.idea.project.description");
  }

  @Override
  public @Nls @NotNull String getDisplayName() {
    return ProjectModelBundle.message("filetype.idea.project.display.name");
  }

  @Override
  public @NotNull String getDefaultExtension() {
    return DEFAULT_EXTENSION;
  }

  @Override
  public Icon getIcon() {
    return IconManager.getInstance().getPlatformIcon(PlatformIcons.IdeaModule);
  }

  @Override
  public boolean isBinary() {
    return false;
  }

  @Override
  public String getCharset(@NotNull VirtualFile file, byte @NotNull [] content) {
    return StandardCharsets.UTF_8.name();
  }
}