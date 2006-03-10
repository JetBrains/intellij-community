package com.intellij.lang.ant;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.util.IconLoader;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class AntFileType extends LanguageFileType {

  @NonNls public static final String DEFAULT_EXTENSION = "ant";
  @NonNls public static final String DOT_DEFAULT_EXTENSION = ".ant";
  private static final Icon ICON = IconLoader.getIcon("/fileTypes/xml.png");

  public AntFileType() {
    super(new AntLanguage());
  }

  @NotNull
  @NonNls
  public String getName() {
    return "ANT";
  }

  @NotNull
  public String getDescription() {
    return IdeBundle.message("filetype.description.ant");
  }

  @NotNull
  @NonNls
  public String getDefaultExtension() {
    return DEFAULT_EXTENSION;
  }

  @Nullable
  public Icon getIcon() {
    return ICON;
  }
}
