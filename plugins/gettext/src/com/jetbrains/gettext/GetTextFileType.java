package com.jetbrains.gettext;

import com.intellij.openapi.fileTypes.LanguageFileType;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Svetlana.Zemlyanskaya
 */
public class GetTextFileType  extends LanguageFileType {
  public static final GetTextFileType INSTANCE = new GetTextFileType();

  private GetTextFileType() {
    super(GetTextLanguage.INSTANCE);
  }

  @NotNull
  public String getName() {
    return "GetText";
  }

  @NotNull
  public String getDescription() {
    return "GNU GetText";
  }

  @NotNull
  public String getDefaultExtension() {
    return "po";
  }

  @Override
  public Icon getIcon() {
    return GetTextIcons.FILETYPE_ICON;
  }
}


