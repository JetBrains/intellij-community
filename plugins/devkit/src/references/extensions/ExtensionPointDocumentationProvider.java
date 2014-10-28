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

import com.intellij.codeInsight.javadoc.JavaDocInfoGenerator;
import com.intellij.lang.documentation.DocumentationProviderEx;
import com.intellij.lang.java.JavaDocumentationProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.PomTarget;
import com.intellij.pom.PomTargetPsiElement;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomTarget;
import com.intellij.util.xml.DomUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.dom.ExtensionPoint;
import org.jetbrains.idea.devkit.util.DescriptorUtil;

/**
 * @author Konstantin Bulenkov
 */
public class ExtensionPointDocumentationProvider extends DocumentationProviderEx {

  @Override
  public String getQuickNavigateInfo(PsiElement element, PsiElement originalElement) {
    ExtensionPoint extensionPoint = findExtensionPoint(element);
    if (extensionPoint == null) return null;

    final XmlFile epDeclarationFile = (XmlFile)extensionPoint.getXmlTag().getContainingFile();
    final Module epModule = ModuleUtilCore.findModuleForFile(epDeclarationFile.getVirtualFile(), element.getProject());
    final String epPrefix = extensionPoint.getNamePrefix();

    final PsiClass epClass = getExtensionPointClass(extensionPoint);
    StringBuilder epClassText = new StringBuilder();
    if (epClass != null) {
      JavaDocInfoGenerator.generateType(epClassText, PsiTypesUtil.getClassType(epClass), epClass, true);
    }
    else {
      epClassText.append("<unknown>");
    }

    return (epModule == null ? "" : "[" + epModule.getName() + "]") +
           (epPrefix == null ? "" : " " + epPrefix) +
           "<br/>" +
           "<b>" + extensionPoint.getEffectiveName() + "</b>" +
           " (" + epDeclarationFile.getName() + ")<br/>" +
           epClassText.toString();
  }

  @Override
  public String generateDoc(PsiElement element, @Nullable PsiElement originalElement) {
    ExtensionPoint extensionPoint = findExtensionPoint(element);
    if (extensionPoint == null) return null;

    final PsiClass epClass = getExtensionPointClass(extensionPoint);
    if (epClass != null) {
      StringBuilder sb = new StringBuilder();
      sb.append("<em>EP Definition</em><br/>");
      final String quickInfo = StringUtil.notNullize(getQuickNavigateInfo(element, originalElement));
      sb.append(quickInfo);
      sb.append("<br/>");
      sb.append("<br/>");
      sb.append("<em>EP Implementation</em>");
      sb.append(JavaDocumentationProvider.generateExternalJavadoc(epClass));

      return sb.toString();
    }
    return null;
  }

  @Nullable
  private static ExtensionPoint findExtensionPoint(PsiElement element) {
    if (element instanceof PomTargetPsiElement &&
        DescriptorUtil.isPluginXml(element.getContainingFile())) {
      final PomTarget pomTarget = ((PomTargetPsiElement)element).getTarget();
      if (pomTarget instanceof DomTarget) {
        final DomElement domElement = ((DomTarget)pomTarget).getDomElement();
        if (domElement instanceof ExtensionPoint) {
          return (ExtensionPoint)domElement;
        }
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