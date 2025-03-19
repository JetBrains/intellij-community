// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mock;

import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.LanguageFileType;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author gregsh
 */
public class MockLanguageFileType extends LanguageFileType{

  private final String myExtension;

  public MockLanguageFileType(@NotNull Language language, String extension) {
    super(language);
    myExtension = extension;
  }

  @Override
  public @NotNull String getName() {
    return getLanguage().getID();
  }

  @Override
  public @NotNull String getDescription() {
    return "";
  }

  @Override
  public @NotNull String getDefaultExtension() {
    return myExtension;
  }

  @Override
  public Icon getIcon() {
    return null;
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof LanguageFileType)) return false;
    return getLanguage().equals(((LanguageFileType)obj).getLanguage());
  }
}
