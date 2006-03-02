package com.intellij.lang.ant;

import com.intellij.lang.Language;
import com.intellij.lang.StdLanguages;
import com.intellij.lang.CompositeLanguage;
import com.intellij.lang.LanguageExtension;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.pom.xml.events.XmlChange;
import org.jetbrains.annotations.NonNls;

public class AntSupport implements ApplicationComponent {

  private static LanguageFileType ourAntFileType = null;
  private static Language ourAntLanguage = null;

  public AntSupport(FileTypeManager fileTypeManager) {
    //fileTypeManager.registerFileType(getFileType(), new String[]{AntFileType.DEFAULT_EXTENSION});
    ((CompositeLanguage)StdLanguages.XML).registerLanguageExtension(new AntLanguageExtension());
  }

  public static LanguageFileType getFileType() {
    if (ourAntFileType == null) {
      ourAntFileType = new AntFileType();
    }
    return ourAntFileType;
  }

  public static Language getLanguage() {
    if (ourAntLanguage == null) {
      ourAntLanguage = getFileType().getLanguage();
    }
    return ourAntLanguage;
  }

  @NonNls
  public String getComponentName() {
    return "AntSupportComponent";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  private static class AntLanguageExtension implements LanguageExtension {
    public boolean isRelevantForFile(final PsiFile psi) {
      if(!(psi instanceof XmlFile)) return false;
      if(!"ant".equals(psi.getViewProvider().getVirtualFile().getExtension())) return false;
      final XmlFile xmlFile = (XmlFile)psi;
      final XmlTag tag = xmlFile.getDocument().getRootTag();
      if(tag == null) return false;
      if ("project".equals(tag.getName()) && tag.getContext() instanceof XmlDocument) {
        if (tag.getAttributeValue("default") != null) {
          return true;
        }
      }
      return false;
    }

    public Language getLanguage() {
      return AntSupport.getLanguage();
    }

    public boolean isAffectedByChange(final XmlChange xmlChange) {
      return true;
    }
  }
}
