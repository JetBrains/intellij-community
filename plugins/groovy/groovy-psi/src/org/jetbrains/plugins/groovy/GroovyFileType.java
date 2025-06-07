// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.util.containers.ContainerUtil;
import icons.JetgroovyIcons;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Collection;
import java.util.LinkedHashSet;

public final class GroovyFileType extends LanguageFileType {
  public static final @NotNull GroovyFileType GROOVY_FILE_TYPE = new GroovyFileType();
  public static final @NonNls String DEFAULT_EXTENSION = "groovy";

  private static final @NlsSafe String GROOVY_DESCRIPTION = "Groovy";

  private GroovyFileType() {
    super(GroovyLanguage.INSTANCE);
  }

  @Override
  public @NotNull @NonNls String getName() {
    return "Groovy";
  }

  @Override
  public @NotNull String getDescription() {
    return GROOVY_DESCRIPTION;
  }

  @Override
  public @NotNull @NonNls String getDefaultExtension() {
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

  public static @NotNull FileType @NotNull [] getGroovyEnabledFileTypes() {
    Collection<FileType> result = new LinkedHashSet<>();
    result.addAll(ContainerUtil.filter(
      FileTypeManager.getInstance().getRegisteredFileTypes(),
      GroovyFileType::isGroovyEnabledFileType
    ));
    return result.toArray(FileType.EMPTY_ARRAY);
  }

  private static boolean isGroovyEnabledFileType(FileType ft) {
    return ft instanceof GroovyEnabledFileType ||
           ft instanceof LanguageFileType && ((LanguageFileType)ft).getLanguage() == GroovyLanguage.INSTANCE;
  }
}
