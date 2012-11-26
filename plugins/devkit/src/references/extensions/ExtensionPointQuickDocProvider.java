/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.idea.devkit.references.extensions;

import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.lang.java.JavaDocumentationProvider;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlToken;
import com.intellij.psi.xml.XmlTokenType;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.inspections.DevKitInspectionBase;

import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public class ExtensionPointQuickDocProvider implements DocumentationProvider {
  @Override
  public String getQuickNavigateInfo(PsiElement element, PsiElement originalElement) {
    return null;
  }

  @Override
  public List<String> getUrlFor(PsiElement element, PsiElement originalElement) {
    return null;
  }

  @Override
  public String generateDoc(PsiElement element, @Nullable PsiElement originalElement) {
    if (originalElement == null) return null;
    if (originalElement.getLanguage() == XMLLanguage.INSTANCE || DevKitInspectionBase.isPluginXml(originalElement.getContainingFile())) {
      final PsiElement context = element.getContext();
      String fqn = null;
      if (originalElement instanceof XmlToken && ((XmlToken)originalElement).getTokenType() == XmlTokenType.XML_NAME) {
        PsiElement attr;
        PsiElement tag;
        if (context != null
            && (attr = context.getParent()) instanceof XmlAttribute
            && (tag = attr.getParent()) instanceof XmlTag) {
          final String interfaceFqn = ((XmlTag)tag).getAttributeValue("interface");
          final String beanClassFqn = ((XmlTag)tag).getAttributeValue("beanClass");
          fqn = interfaceFqn == null ? beanClassFqn : interfaceFqn;
        }
      }

      if (fqn != null) {
        final Project project = element.getProject();
        final PsiClass psiClass = JavaPsiFacade.getInstance(project).findClass(fqn, GlobalSearchScope.allScope(project));
        if (psiClass != null) {
          return new JavaDocumentationProvider().generateExternalJavadoc(psiClass);
        }
      }

    }
    return null;
  }

  @Override
  public PsiElement getDocumentationElementForLookupItem(PsiManager psiManager, Object object, PsiElement element) {
    return null;
  }

  @Override
  public PsiElement getDocumentationElementForLink(PsiManager psiManager, String link, PsiElement context) {
    return null;
  }
}
