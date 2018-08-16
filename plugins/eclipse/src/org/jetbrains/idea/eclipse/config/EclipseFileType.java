// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.eclipse.config;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import icons.EclipseIcons;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.eclipse.EclipseBundle;
import org.jetbrains.idea.eclipse.EclipseXml;

import javax.swing.*;

public class EclipseFileType implements FileType {
  public static final FileType INSTANCE = new EclipseFileType();

  @Override
  @NotNull
  @NonNls
  public String getName() {
    return "Eclipse";
  }

  @Override
  @NotNull
  public String getDescription() {
    return EclipseBundle.message("eclipse.file.type.descr");
  }

  @Override
  @NotNull
  @NonNls
  public String getDefaultExtension() {
    return EclipseXml.CLASSPATH_EXT;
  }

  @Override
  @Nullable
  public Icon getIcon() {
    return EclipseIcons.Eclipse;
  }

  @Override
  public boolean isBinary() {
    return false;
  }

  @Override
  public boolean isReadOnly() {
    return false;
  }

  @Override
  public String getCharset(@NotNull final VirtualFile file, @NotNull final byte[] content) {
    return CharsetToolkit.UTF8;
  }
}
