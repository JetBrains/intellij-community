// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.eclipse.config;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.fileTypes.FileType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.eclipse.EclipseBundle;
import org.jetbrains.idea.eclipse.EclipseXml;

import javax.swing.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

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
    return AllIcons.Providers.Eclipse;
  }

  @Override
  public boolean isBinary() {
    return false;
  }

  @Override
  public @NotNull CharsetHint getCharsetHint() {
    return new CharsetHint.ForcedCharset(StandardCharsets.UTF_8);
  }
}
