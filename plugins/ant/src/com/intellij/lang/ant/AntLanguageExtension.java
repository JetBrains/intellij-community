package com.intellij.lang.ant;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageFilter;
import com.intellij.lang.ant.psi.impl.AntFileImpl;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;

public class AntLanguageExtension implements LanguageFilter {

  public boolean isRelevantForFile(final PsiFile psi) {
    if (psi instanceof XmlFile) {
      if (isAntFile((XmlFile)psi)) return true;
    }
    return false;
  }

  public static boolean isAntFile(final XmlFile xmlFile) {
    final XmlDocument document = xmlFile.getDocument();
    if (document != null) {
      final XmlTag tag = document.getRootTag();
      if (tag != null && AntFileImpl.PROJECT_TAG.equals(tag.getName()) && tag.getContext() instanceof XmlDocument) {
        if (tag.getAttributeValue(AntFileImpl.DEFAULT_ATTR) != null) {
          return true;
        }
        VirtualFile vFile = xmlFile.getVirtualFile();
        if (vFile == null) {
          final PsiFile origFile = xmlFile.getOriginalFile();
          if (origFile != null) {
            vFile = origFile.getVirtualFile();
          }
        }
        if (vFile != null && ForcedAntFileAttribute.isAntFile(vFile)) {
          return true;
        }
      }
    }
    return false;
  }

  public Language getLanguage() {
    return AntSupport.getLanguage();
  }

}
