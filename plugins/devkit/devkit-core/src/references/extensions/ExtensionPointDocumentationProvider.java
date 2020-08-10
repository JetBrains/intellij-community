// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.references.extensions;

import com.intellij.codeInsight.documentation.DocumentationManager;
import com.intellij.codeInsight.documentation.DocumentationManagerProtocol;
import com.intellij.codeInsight.javadoc.JavaDocUtil;
import com.intellij.lang.documentation.DocumentationMarkup;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.pom.PomTarget;
import com.intellij.pom.PomTargetPsiElement;
import com.intellij.psi.*;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomTarget;
import com.intellij.util.xml.DomUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.dom.ExtensionPoint;
import org.jetbrains.idea.devkit.util.DescriptorUtil;

public class ExtensionPointDocumentationProvider implements DocumentationProvider {

  @Override
  public String getQuickNavigateInfo(PsiElement element, PsiElement originalElement) {
    ExtensionPoint extensionPoint = findExtensionPoint(element);
    if (extensionPoint == null) return null;

    final XmlFile epDeclarationFile = DomUtil.getFile(extensionPoint);

    final Module epModule = ModuleUtilCore.findModuleForFile(epDeclarationFile.getVirtualFile(), element.getProject());
    HtmlBuilder builder = new HtmlBuilder();
    if (epModule != null) {
      builder.append("[" + epModule.getName() + "]").br();
    }

    builder.append(HtmlChunk.text(extensionPoint.getEffectiveQualifiedName()).bold());
    builder.append(" ");
    builder.append("(" + epDeclarationFile.getName() + ")");
    builder.br();

    if (DomUtil.hasXml(extensionPoint.getBeanClass())) {
      builder.append(generateClassLink(extensionPoint.getBeanClass().getValue()));
      builder.br();
    }

    final PsiClass extensionPointClass = extensionPoint.getExtensionPointClass();
    builder.append(generateClassLink(extensionPointClass));

    return builder.toString();
  }

  @Override
  public String generateDoc(PsiElement element, @Nullable PsiElement originalElement) {
    ExtensionPoint extensionPoint = findExtensionPoint(element);
    if (extensionPoint == null) return null;

    HtmlBuilder defBuilder = new HtmlBuilder();
    defBuilder.append(HtmlChunk.text(extensionPoint.getEffectiveQualifiedName()).bold());
    defBuilder.br().append(DomUtil.getFile(extensionPoint).getName());

    final PsiClass beanClass = extensionPoint.getBeanClass().getValue();
    if (beanClass != null) {
      defBuilder.append(generateClassDoc(beanClass));

      HtmlBuilder bindingRows = new HtmlBuilder();

      new ExtensionPointBinding(beanClass).visit(new ExtensionPointBinding.BindingVisitor() {
        @Override
        public void visitAttribute(@NotNull PsiField field, @NotNull String attributeName, boolean required) {
          appendFieldBindingText(field, attributeName, required);
        }

        @Override
        public void visitTagOrProperty(@NotNull PsiField field, @NotNull String tagName, boolean required) {
          visitAttribute(field, "<" + tagName + ">", required);
        }

        @Override
        public void visitXCollection(@NotNull PsiField field,
                                     @Nullable String tagName,
                                     @NotNull PsiAnnotation collectionAnnotation,
                                     boolean required) {
          visitAttribute(field, "<" + tagName + ">...", required);
        }

        private void appendFieldBindingText(@NotNull PsiField field, @NotNull @NlsSafe String displayName, boolean required) {
          HtmlChunk hyperLink = createLink(JavaDocUtil.getReferenceText(field.getProject(), field), displayName);

          final String typeText = field.getType().getPresentableText();
          final String initializer = field.getInitializer() != null ? " = " + field.getInitializer().getText() : "";

          bindingRows.append(createSectionRow(hyperLink, typeText + (required ? " (required)" : "") + initializer));
        }
      });

      if (!bindingRows.isEmpty()) {
        defBuilder.append(bindingRows.br().wrapWith(DocumentationMarkup.SECTIONS_TABLE));
      }
    }
    HtmlChunk.Element definition = defBuilder.wrapWith("pre").wrapWith(DocumentationMarkup.DEFINITION_ELEMENT);

    HtmlBuilder builder = new HtmlBuilder();
    builder.append(definition);

    final PsiClass extensionPointClass = extensionPoint.getExtensionPointClass();
    if (extensionPointClass != null) { // e.g. ServiceDescriptor
      HtmlBuilder content = new HtmlBuilder();
      content.append(HtmlChunk.text("Extension Point Implementation").wrapWith("h2"));
      content.append(generateClassDoc(extensionPointClass));
      builder.append(content.wrapWith(DocumentationMarkup.CONTENT_ELEMENT));
    }

    return builder.toString();
  }

  @Override
  public PsiElement getDocumentationElementForLink(PsiManager psiManager, String link, PsiElement context) {
    return JavaDocUtil.findReferenceTarget(psiManager, link, context);
  }

  private static HtmlChunk generateClassLink(@Nullable PsiClass epClass) {
    if (epClass == null) return HtmlChunk.empty();

    return createLink(epClass.getQualifiedName(), epClass.getName());
  }

  private static HtmlChunk createLink(String refText, @Nls String label) {
    HtmlChunk text = HtmlChunk.text(label).wrapWith("code");
    String link = DocumentationManagerProtocol.PSI_ELEMENT_PROTOCOL + refText;
    return HtmlChunk.tag("a").attr("href", link).child(text);
  }

  private static HtmlChunk generateClassDoc(@Nullable PsiElement element) {
    if (element == null) {
      return HtmlChunk.text("??? not found ???");
    }

    final DocumentationProvider documentationProvider = DocumentationManager.getProviderFromElement(element);
    return HtmlChunk.raw(documentationProvider.generateDoc(element, null));
  }

  private static HtmlChunk createSectionRow(HtmlChunk sectionName, @Nls String sectionContent) {
    HtmlChunk headerCell = DocumentationMarkup.SECTION_HEADER_CELL.child(sectionName.wrapWith("p"));
    HtmlChunk contentCell = DocumentationMarkup.SECTION_CONTENT_CELL.addText(sectionContent);
    return HtmlChunk.tag("tr").children(headerCell, contentCell);
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