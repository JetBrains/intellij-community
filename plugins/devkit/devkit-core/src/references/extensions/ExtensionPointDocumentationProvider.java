/*
 * Copyright 2000-2018 JetBrains s.r.o.
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
import com.intellij.codeInsight.javadoc.JavaDocUtil;
import com.intellij.lang.documentation.DocumentationMarkup;
import com.intellij.lang.documentation.DocumentationProviderEx;
import com.intellij.lang.java.JavaDocumentationProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.PomTarget;
import com.intellij.pom.PomTargetPsiElement;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomTarget;
import com.intellij.util.xml.DomUtil;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.dom.ExtensionPoint;
import org.jetbrains.idea.devkit.dom.With;
import org.jetbrains.idea.devkit.util.DescriptorUtil;

import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public class ExtensionPointDocumentationProvider extends DocumentationProviderEx {

  @Override
  public String getQuickNavigateInfo(PsiElement element, PsiElement originalElement) {
    ExtensionPoint extensionPoint = findExtensionPoint(element);
    if (extensionPoint == null) return null;

    final XmlFile epDeclarationFile = DomUtil.getFile(extensionPoint);
    final Module epModule = ModuleUtilCore.findModuleForFile(epDeclarationFile.getVirtualFile(), element.getProject());
    final String epPrefix = extensionPoint.getNamePrefix();

    final PsiClass epClass = getExtensionPointClass(extensionPoint);
    StringBuilder epClassText = new StringBuilder();
    if (epClass != null) {
      generateClassLink(epClassText, epClass);
    }
    else {
      epClassText.append("<unknown>");
    }

    String moduleAndPrefix = (epModule == null ? "" : "[" + epModule.getName() + "]") +
                             (epPrefix == null ? "" : " " + epPrefix);
    if (!moduleAndPrefix.isEmpty()) {
      moduleAndPrefix += "<br/>";
    }
    return moduleAndPrefix +
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
      StringBuilder sb = new StringBuilder(DocumentationMarkup.DEFINITION_START);
      sb.append("<b>").append(extensionPoint.getEffectiveName()).append("</b>");
      String namePrefix = extensionPoint.getNamePrefix();
      if (StringUtil.isNotEmpty(namePrefix)) {
        sb.append(" [").append(namePrefix).append("]");
      }
      sb.append("<br>");
      generateClassLink(sb, epClass);
      sb.append("<br>").append(DomUtil.getFile(extensionPoint).getName());

      List<With> withElements = extensionPoint.getWithElements();
      if (!withElements.isEmpty()) {
        sb.append(DocumentationMarkup.SECTIONS_START);
        for (With withElement: withElements) {

          String name = DomUtil.hasXml(withElement.getAttribute())
                        ? withElement.getAttribute().getStringValue()
                        : "<" + withElement.getTag().getStringValue() + ">";

          StringBuilder classLinkSb = new StringBuilder();
          generateClassLink(classLinkSb, withElement.getImplements().getValue());

          appendSection(sb, XmlUtil.escape(name), classLinkSb.toString());
        }
        sb.append(DocumentationMarkup.SECTIONS_END);
      }
      sb.append(DocumentationMarkup.DEFINITION_END);

      sb.append(DocumentationMarkup.CONTENT_START);
      String epDocumentationType = DomUtil.hasXml(extensionPoint.getBeanClass()) ? "Bean Class" : "Implementation Class";
      sb.append("<em>Extension Point ").append(epDocumentationType).append("</em>");
      sb.append(JavaDocumentationProvider.generateExternalJavadoc(epClass));
      sb.append(DocumentationMarkup.CONTENT_END);

      return sb.toString();
    }
    return null;
  }

  @Override
  public PsiElement getDocumentationElementForLink(PsiManager psiManager, String link, PsiElement context) {
    return JavaDocUtil.findReferenceTarget(psiManager, link, context);
  }

  private static void generateClassLink(StringBuilder epClassText, @Nullable PsiClass epClass) {
    if (epClass == null) return;
    JavaDocInfoGenerator.generateType(epClassText, PsiTypesUtil.getClassType(epClass), epClass, true);
  }

  private static void appendSection(StringBuilder sb, String sectionName, String sectionContent) {
    sb.append(DocumentationMarkup.SECTION_HEADER_START).append(sectionName).append(":")
      .append(DocumentationMarkup.SECTION_SEPARATOR);
    sb.append(sectionContent);
    sb.append(DocumentationMarkup.SECTION_END);
  }

  @Nullable
  private static ExtensionPoint findExtensionPoint(PsiElement element) {
    // via @NameValue
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
    // via XmlTag
    else if (element instanceof XmlTag &&
             DescriptorUtil.isPluginXml(element.getContainingFile())) {
      DomElement domElement = DomUtil.getDomElement(element);
      if (domElement instanceof ExtensionPoint) {
        return (ExtensionPoint)domElement;
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