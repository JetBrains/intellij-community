// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.eclipse.config;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.eclipse.EclipseBundle;
import org.jetbrains.idea.eclipse.EclipseXml;

import javax.swing.*;

public class EclipseFileType implements FileType {
  public static final FileType INSTANCE = new EclipseFileType();

  private EclipseFileType() {
  }

  @Override
  @NotNull
  @NonNls
  public String getName() {
    return "Eclipse";
  }

  @Override
  @NotNull
  public String getDescription() {
    return EclipseBundle.message("filetype.eclipse.description");
  }

  @Nls
  @Override
  public @NotNull String getDisplayName() {
    return EclipseBundle.message("filetype.eclipse.display.name");
  }

  @Override
  @NotNull
  @NonNls
  public String getDefaultExtension() {
    return EclipseXml.CLASSPATH_EXT;
  }

  @Override
  public Icon getIcon() {
    return AllIcons.Providers.Eclipse;
  }

  @Override
  public boolean isBinary() {
    return false;
  }

  @Override
  public String getCharset(@NotNull VirtualFile file, final byte @NotNull [] content) {
    return CharsetToolkit.UTF8;
  }
}
