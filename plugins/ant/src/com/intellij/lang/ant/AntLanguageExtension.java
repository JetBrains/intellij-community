package com.intellij.lang.ant;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageExtension;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.xml.events.XmlChange;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;

public class AntLanguageExtension implements LanguageExtension {

  public static Key<Boolean> ANT_FILE_SIGN = new Key<Boolean>("FORCED ANT FILE");

  public boolean isRelevantForFile(final PsiFile psi) {
    if (psi instanceof XmlFile) {
      final XmlFile xmlFile = (XmlFile)psi;
      final XmlDocument document = xmlFile.getDocument();
      if (document != null) {
        final XmlTag tag = document.getRootTag();
        if (tag != null && "project".equals(tag.getName()) && tag.getContext() instanceof XmlDocument) {
          if (tag.getAttributeValue("default") != null) {
            return true;
          }
          final VirtualFile file = xmlFile.getVirtualFile();
          if (file != null && file.getUserData(ANT_FILE_SIGN) != null) {
            return true;
          }
        }
      }
    }
    return false;
  }

  public Language getLanguage() {
    return AntSupport.getLanguage();
  }

  public boolean isAffectedByChange(final XmlChange xmlChange) {
    xmlChange.accept(AntSupport.getChangeVisitor());
    return false;
  }
}
