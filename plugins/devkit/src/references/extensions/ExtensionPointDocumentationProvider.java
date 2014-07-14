/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.lang.documentation.DocumentationProviderEx;
import com.intellij.lang.java.JavaDocumentationProvider;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlToken;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.dom.Extension;
import org.jetbrains.idea.devkit.dom.ExtensionPoint;
import org.jetbrains.idea.devkit.util.DescriptorUtil;

/**
 * @author Konstantin Bulenkov
 */
public class ExtensionPointDocumentationProvider extends DocumentationProviderEx {

  @Override
  public String getQuickNavigateInfo(PsiElement element, PsiElement originalElement) {
    ExtensionPoint extensionPoint = findExtensionPoint(originalElement);
    if (extensionPoint == null) return null;

    final XmlFile epDeclarationFile = (XmlFile)extensionPoint.getXmlTag().getContainingFile();
    final Module epModule = ModuleUtilCore.findModuleForFile(epDeclarationFile.getVirtualFile(), element.getProject());
    final PsiClass epClass = getExtensionPointClass(extensionPoint);
    final String epPrefix = extensionPoint.getNamePrefix();
    return
      (epModule == null ? "" : "[" + epModule.getName() + "]") +
      (epPrefix == null ? "" : " " + epPrefix) +
      "\n" +
      "<b>" + extensionPoint.getEffectiveName() + "</b>" +
      " [" + epDeclarationFile.getName() + "]\n" +
      (epClass == null ? "<unknown>" : epClass.getQualifiedName());
  }

  @Override
  public String generateDoc(PsiElement element, @Nullable PsiElement originalElement) {
    ExtensionPoint extensionPoint = findExtensionPoint(originalElement);
    if (extensionPoint == null) return null;

    final PsiClass epClass = getExtensionPointClass(extensionPoint);
    if (epClass != null) {
      return JavaDocumentationProvider.generateExternalJavadoc(epClass);
    }
    return null;
  }

  @Nullable
  private static ExtensionPoint findExtensionPoint(@Nullable PsiElement element) {
    if (element == null) return null;
    if (element.getLanguage() == XMLLanguage.INSTANCE &&
        (element instanceof XmlTag ||
         element instanceof XmlToken && ((XmlToken)element).getTokenType() == XmlTokenType.XML_NAME) &&
        DescriptorUtil.isPluginXml(element.getContainingFile())) {
      final DomElement domElement = DomUtil.getDomElement(element);
      if (domElement instanceof Extension) {
        return ((Extension)domElement).getExtensionPoint();
      }
    }
    return null;
  }

  @Nullable
  private static PsiClass getExtensionPointClass(ExtensionPoint extensionPoint) {
    return DomUtil.hasXml(extensionPoint.getInterface()) ?
           extensionPoint.getInterface().getValue() : extensionPoint.getBeanClass().getValue();
  }
}