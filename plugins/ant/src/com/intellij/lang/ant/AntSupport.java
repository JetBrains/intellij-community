package com.intellij.lang.ant;

import com.intellij.lang.CompositeLanguage;
import com.intellij.lang.StdLanguages;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.LanguageFileType;
import org.jetbrains.annotations.NonNls;

public class AntSupport implements ApplicationComponent {

  private static LanguageFileType ourAntFileType = null;
  private static AntLanguage ourAntLanguage = null;

  public AntSupport(FileTypeManager fileTypeManager) {
    fileTypeManager.getRegisteredFileTypes();
    ((CompositeLanguage)StdLanguages.XML).registerLanguageExtension(new AntLanguageExtension());
  }

  public static AntLanguage getLanguage() {
    if (ourAntLanguage == null) {
      if (ourAntFileType == null) {
        ourAntFileType = new AntFileType();
      }
      ourAntLanguage = (AntLanguage)ourAntFileType.getLanguage();
    }
    return ourAntLanguage;
  }

  @NonNls
  public String getComponentName() {
    return "AntSupport";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }
}
