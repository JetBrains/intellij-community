// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import icons.JetgroovyIcons;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class GroovyFileType extends LanguageFileType {
  public static final List<FileType> GROOVY_FILE_TYPES = new ArrayList<>();
  public static final @NotNull GroovyFileType GROOVY_FILE_TYPE = new GroovyFileType();
  @NonNls public static final String DEFAULT_EXTENSION = "groovy";

  private GroovyFileType() {
    super(GroovyLanguage.INSTANCE);
    GROOVY_FILE_TYPES.add(this);
  }

  @NotNull
  public static FileType[] getGroovyEnabledFileTypes() {
    return GROOVY_FILE_TYPES.toArray(FileType.EMPTY_ARRAY);
  }

  @Override
  @NotNull
  @NonNls
  public String getName() {
    return "Groovy";
  }

  @Override
  @NonNls
  @NotNull
  public String getDescription() {
    return "Groovy";
  }

  @Override
  @NotNull
  @NonNls
  public String getDefaultExtension() {
    return DEFAULT_EXTENSION;
  }

  @Override
  public Icon getIcon() {
    return JetgroovyIcons.Groovy.Groovy_16x16;
  }

  @Override
  public boolean isJVMDebuggingSupported() {
    return true;
  }
}
