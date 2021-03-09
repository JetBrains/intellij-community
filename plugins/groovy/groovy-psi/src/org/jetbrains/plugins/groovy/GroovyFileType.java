// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.util.containers.ContainerUtil;
import icons.JetgroovyIcons;
import org.jetbrains.annotations.ApiStatus.ScheduledForRemoval;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

public final class GroovyFileType extends LanguageFileType {

  /**
   * @deprecated implement {@link GroovyEnabledFileType} in the FileType,
   * or extend {@link LanguageFileType} and specify {@link GroovyLanguage#INSTANCE}
   */
  @SuppressWarnings("DeprecatedIsStillUsed")
  @ScheduledForRemoval(inVersion = "2021.1")
  @Deprecated
  public static final List<FileType> GROOVY_FILE_TYPES = new ArrayList<>();
  public static final @NotNull GroovyFileType GROOVY_FILE_TYPE = new GroovyFileType();
  @NonNls public static final String DEFAULT_EXTENSION = "groovy";

  private GroovyFileType() {
    super(GroovyLanguage.INSTANCE);
  }

  @Override
  @NotNull
  @NonNls
  public String getName() {
    return "Groovy";
  }

  @Override
  @NotNull
  public String getDescription() {
    return GroovyBundle.message("filetype.groovy.description");
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

  public static @NotNull FileType @NotNull [] getGroovyEnabledFileTypes() {
    Collection<FileType> result = new LinkedHashSet<>(GROOVY_FILE_TYPES);
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
