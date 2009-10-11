/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
        VirtualFile vFile = xmlFile.getOriginalFile().getVirtualFile();
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
