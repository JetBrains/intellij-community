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

  @NotNull
  @Override
  public String getName() {
    return getLanguage().getID();
  }

  @NotNull
  @Override
  public String getDescription() {
    return "";
  }

  @NotNull
  @Override
  public String getDefaultExtension() {
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
