package com.intellij.lang.ant;

import com.intellij.lang.CompositeLanguage;
import com.intellij.lang.StdLanguages;
import com.intellij.lang.ant.psi.changes.AntChangeVisitor;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.LanguageFileType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class AntSupport implements ApplicationComponent {

  private static LanguageFileType ourFileType = null;
  private static AntLanguage ourLanguage = null;
  private static AntChangeVisitor ourChangeVisitor = null;

  public AntSupport(FileTypeManager fileTypeManager) {
    fileTypeManager.getRegisteredFileTypes();
    ((CompositeLanguage)StdLanguages.XML).registerLanguageExtension(new AntLanguageExtension());
  }

  public static AntLanguage getLanguage() {
    if (ourLanguage == null) {
      if (ourFileType == null) {
        ourFileType = new AntFileType();
      }
      ourLanguage = (AntLanguage)ourFileType.getLanguage();
    }
    return ourLanguage;
  }

  public static AntChangeVisitor getChangeVisitor() {
    if (ourChangeVisitor == null) {
      ourChangeVisitor = new AntChangeVisitor();
    }
    return ourChangeVisitor;
  }

  @NotNull
  @NonNls
  public String getComponentName() {
    return "AntSupport";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }
}
