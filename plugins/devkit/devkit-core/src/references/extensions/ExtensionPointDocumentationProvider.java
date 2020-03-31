// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.references.extensions;

import com.intellij.codeInsight.documentation.DocumentationManager;
import com.intellij.codeInsight.documentation.DocumentationManagerUtil;
import com.intellij.codeInsight.javadoc.JavaDocUtil;
import com.intellij.lang.documentation.DocumentationMarkup;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.PomTarget;
import com.intellij.pom.PomTargetPsiElement;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
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

public class ExtensionPointDocumentationProvider implements DocumentationProvider {

  @Override
  public String getQuickNavigateInfo(PsiElement element, PsiElement originalElement) {
    ExtensionPoint extensionPoint = findExtensionPoint(element);
    if (extensionPoint == null) return null;

    final XmlFile epDeclarationFile = DomUtil.getFile(extensionPoint);

    StringBuilder epClassesText = new StringBuilder();
    if (DomUtil.hasXml(extensionPoint.getBeanClass())) {
      generateClassLink(epClassesText, extensionPoint.getBeanClass().getValue());
      epClassesText.append("<br/>");
    }

    final PsiClass extensionPointClass = extensionPoint.getExtensionPointClass();
    generateClassLink(epClassesText, extensionPointClass);

    final Module epModule = ModuleUtilCore.findModuleForFile(epDeclarationFile.getVirtualFile(), element.getProject());
    String moduleName = (epModule == null ? "" : "[" + epModule.getName() + "]<br/>");

    return moduleName +
           "<b>" + extensionPoint.getEffectiveQualifiedName() + "</b>" +
           " (" + epDeclarationFile.getName() + ")<br/>" +
           epClassesText.toString();
  }

  @Override
  public String generateDoc(PsiElement element, @Nullable PsiElement originalElement) {
    ExtensionPoint extensionPoint = findExtensionPoint(element);
    if (extensionPoint == null) return null;

    StringBuilder sb = new StringBuilder(DocumentationMarkup.DEFINITION_START);
    sb.append("<b>").append(extensionPoint.getEffectiveQualifiedName()).append("</b>");
    sb.append("<br>").append(DomUtil.getFile(extensionPoint).getName());

    if (DomUtil.hasXml(extensionPoint.getBeanClass())) {
      generateClassDoc(sb, extensionPoint.getBeanClass().getValue());
    }

    List<With> withElements = extensionPoint.getWithElements();
    if (!withElements.isEmpty()) {
      sb.append(DocumentationMarkup.SECTIONS_START);
      for (With withElement : withElements) {

        String name = StringUtil.notNullize(DomUtil.hasXml(withElement.getAttribute())
                                            ? withElement.getAttribute().getStringValue()
                                            : "<" + withElement.getTag().getStringValue() + ">");

        StringBuilder classLinkSb = new StringBuilder();
        generateClassLink(classLinkSb, withElement.getImplements().getValue());

        appendSection(sb, XmlUtil.escape(name), classLinkSb.toString());
      }
      sb.append(DocumentationMarkup.SECTIONS_END);
    }

    sb.append(DocumentationMarkup.DEFINITION_END);


    final PsiClass extensionPointClass = extensionPoint.getExtensionPointClass();
    if (extensionPointClass != null) { // e.g. ServiceDescriptor
      sb.append(DocumentationMarkup.CONTENT_START);
      sb.append("<h2>Extension Point Implementation</h2>");
      generateClassDoc(sb, extensionPointClass);
      sb.append(DocumentationMarkup.CONTENT_END);
    }

    return sb.toString();
  }

  @Override
  public PsiElement getDocumentationElementForLink(PsiManager psiManager, String link, PsiElement context) {
    return JavaDocUtil.findReferenceTarget(psiManager, link, context);
  }

  private static void generateClassLink(StringBuilder epClassText, @Nullable PsiClass epClass) {
    if (epClass == null) return;

    DocumentationManagerUtil.createHyperlink(epClassText, epClass, epClass.getQualifiedName(), epClass.getName(), false);
  }

  private static void generateClassDoc(StringBuilder sb, @Nullable PsiElement element) {
    if (element == null) {
      sb.append("??? not found ???");
      return;
    }

    final DocumentationProvider documentationProvider = DocumentationManager.getProviderFromElement(element);
    sb.append(documentationProvider.generateDoc(element, null));
  }

  private static void appendSection(StringBuilder sb, String sectionName, String sectionContent) {
    sb.append(DocumentationMarkup.SECTION_HEADER_START).append(sectionName).append(":")
      .append(DocumentationMarkup.SECTION_SEPARATOR);
    sb.append(sectionContent);
    sb.append(DocumentationMarkup.SECTION_END);
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
    } // via XmlTag for "qualifiedName"
    else if (element instanceof XmlTag &&
             DescriptorUtil.isPluginXml(element.getContainingFile())) {
      DomElement domElement = DomUtil.getDomElement(element);
      if (domElement instanceof ExtensionPoint) {
        return (ExtensionPoint)domElement;
      }
    }

    return null;
  }
}