// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.references.extensions;

import com.intellij.codeInsight.documentation.DocumentationManager;
import com.intellij.codeInsight.documentation.DocumentationManagerProtocol;
import com.intellij.codeInsight.javadoc.JavaDocUtil;
import com.intellij.icons.AllIcons;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.lang.documentation.DocumentationMarkup;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.PomTargetPsiElement;
import com.intellij.psi.*;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomFileElement;
import com.intellij.util.xml.DomUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.dom.ExtensionPoint;
import org.jetbrains.idea.devkit.dom.IdeaPlugin;
import org.jetbrains.idea.devkit.util.DescriptorUtil;

import static com.intellij.lang.documentation.DocumentationMarkup.DEFINITION_ELEMENT;
import static com.intellij.lang.documentation.DocumentationMarkup.PRE_ELEMENT;

final class ExtensionPointDocumentationProvider implements DocumentationProvider {

  @Override
  public @Nls String getQuickNavigateInfo(PsiElement element, PsiElement originalElement) {
    ExtensionPoint extensionPoint = findExtensionPoint(element);
    if (extensionPoint == null) return null;

    final XmlFile epDeclarationFile = DomUtil.getFile(extensionPoint);

    return new HtmlBuilder()
      .append(epModule(element, epDeclarationFile))
      .append(epQualifiedNameAndFileName(extensionPoint, epDeclarationFile))
      .append(epBeanClassLinkOrEmpty(extensionPoint))
      .append(classLink(extensionPoint.getExtensionPointClass()))
      .toString();
  }

  private static HtmlChunk epModule(PsiElement element, XmlFile epDeclarationFile) {
    final Module epModule = ModuleUtilCore.findModuleForFile(epDeclarationFile.getVirtualFile(), element.getProject());
    if (epModule != null) {
      return HtmlChunk.fragment(
        HtmlChunk.text("[" + epModule.getName() + "]"),
        HtmlChunk.br()
      );
    }
    return HtmlChunk.empty();
  }

  private static HtmlChunk epQualifiedNameAndFileName(ExtensionPoint extensionPoint, XmlFile epDeclarationFile) {
    return HtmlChunk.fragment(
      HtmlChunk.text(extensionPoint.getEffectiveQualifiedName()).bold(),
      HtmlChunk.text(" "),
      HtmlChunk.text("(" + epDeclarationFile.getName() + ")"),
      HtmlChunk.br()
    );
  }

  private static HtmlChunk epBeanClassLinkOrEmpty(ExtensionPoint extensionPoint) {
    if (DomUtil.hasXml(extensionPoint.getBeanClass())) {
      return HtmlChunk.fragment(
        classLink(extensionPoint.getBeanClass().getValue()),
        HtmlChunk.br()
      );
    }
    return HtmlChunk.empty();
  }

  @Override
  public @Nls String generateDoc(PsiElement element, @Nullable PsiElement originalElement) {
    ExtensionPoint extensionPoint = findExtensionPoint(element);
    if (extensionPoint == null) return null;
    return new HtmlBuilder()
      .append(epQualifiedNameAndFileName(extensionPoint))
      .append(epBeanDocAndFields(extensionPoint))
      .append(epClassDoc(extensionPoint))
      .append(platformExplorerLink(extensionPoint))
      .toString();
  }

  private static HtmlChunk epQualifiedNameAndFileName(ExtensionPoint extensionPoint) {
    return HtmlChunk.fragment(
      HtmlChunk.text(extensionPoint.getEffectiveQualifiedName()).bold().wrapWith(PRE_ELEMENT),
      HtmlChunk.icon("AllIcons.Nodes.Plugin", AllIcons.Nodes.Plugin),
      HtmlChunk.nbsp(),
      HtmlChunk.text(getNameAndOptionalPluginId(extensionPoint))
    ).wrapWith(DEFINITION_ELEMENT);
  }

  private static @Nls @NotNull String getNameAndOptionalPluginId(ExtensionPoint extensionPoint) {
    XmlFile file = DomUtil.getFile(extensionPoint);
    String fileName = file.getName();
    if (!PluginManagerCore.PLUGIN_XML.equals(fileName)) {
      return fileName;
    }
    DomFileElement<DomElement> element = DomUtil.getFileElement(extensionPoint);
    if (element == null) return fileName;
    DomElement rootElement = element.getRootElement();
    if (rootElement instanceof IdeaPlugin ideaPlugin) {
      String pluginId = ideaPlugin.getId().getStringValue();
      if (StringUtil.isNotEmpty(pluginId)) {
        return fileName + " (" + pluginId + ")";
      }
    }
    return fileName;
  }

  private static @NotNull HtmlChunk epBeanDocAndFields(ExtensionPoint extensionPoint) {
    final PsiClass beanClass = extensionPoint.getBeanClass().getValue();
    if (beanClass != null) {
      return new HtmlBuilder()
        .append(generateClassDoc(beanClass))
        .append(epBeanFields(beanClass))
        .wrapWith(PRE_ELEMENT)
        .wrapWith(DEFINITION_ELEMENT);
    }
    return HtmlChunk.empty();
  }

  private static @NotNull HtmlChunk epBeanFields(PsiClass beanClass) {
    HtmlBuilder bindingRows = new HtmlBuilder();

    new ExtensionPointBinding(beanClass).visit(new ExtensionPointBinding.BindingVisitor() {
      @Override
      public void visitAttribute(@NotNull PsiField field, @NotNull String attributeName, RequiredFlag required) {
        appendFieldBindingText(field, attributeName, required);
      }

      @Override
      public void visitTagOrProperty(@NotNull PsiField field, @NotNull String tagName, RequiredFlag required) {
        appendFieldBindingText(field, "<" + tagName + ">", required);
      }

      @Override
      public void visitXCollection(@NotNull PsiField field,
                                   @Nullable String tagName,
                                   @NotNull PsiAnnotation collectionAnnotation,
                                   RequiredFlag required) {
        appendFieldBindingText(field, "<" + tagName + ">...", required);
      }

      private void appendFieldBindingText(@NotNull PsiField field, @NotNull @NlsSafe String displayName, RequiredFlag required) {
        HtmlChunk hyperLink = createLink(JavaDocUtil.getReferenceText(field.getProject(), field), displayName);

        final String typeText = field.getType().getPresentableText();
        String requiredText = "";
        if (required == RequiredFlag.REQUIRED) {
          requiredText = " " + DevKitBundle.message("extension.point.documentation.field.required.suffix");
        }
        else if (required == RequiredFlag.REQUIRED_ALLOW_EMPTY) {
          requiredText = " " + DevKitBundle.message("extension.point.documentation.field.required.can.be.empty.suffix");
        }
        final String initializer = field.getInitializer() != null ? " = " + field.getInitializer().getText() : "";
        bindingRows.append(createSectionRow(hyperLink, typeText + requiredText + initializer));
      }
    });

    if (!bindingRows.isEmpty()) {
      return bindingRows.br().wrapWith(DocumentationMarkup.SECTIONS_TABLE);
    }
    return HtmlChunk.empty();
  }

  private static @NotNull HtmlChunk epClassDoc(ExtensionPoint extensionPoint) {
    final PsiClass extensionPointClass = extensionPoint.getExtensionPointClass();
    if (extensionPointClass != null) { // e.g. ServiceDescriptor
      HtmlBuilder content = new HtmlBuilder();
      content.append(HtmlChunk.text(DevKitBundle.message("extension.point.documentation.implementation.section")).wrapWith("h2"));
      content.append(generateClassDoc(extensionPointClass));
      return content.wrapWith(DocumentationMarkup.CONTENT_ELEMENT);
    }
    return HtmlChunk.empty();
  }

  private static @NotNull HtmlChunk platformExplorerLink(ExtensionPoint extensionPoint) {
    HtmlBuilder platformExplorerLink = new HtmlBuilder();
    String ipeLink = "https://jb.gg/ipe?extensions=" + extensionPoint.getEffectiveQualifiedName();
    return platformExplorerLink.appendLink(ipeLink, DevKitBundle.message("extension.point.documentation.link.platform.explorer"))
      .wrapWith(DocumentationMarkup.CONTENT_ELEMENT);
  }

  @Override
  public PsiElement getDocumentationElementForLink(PsiManager psiManager, String link, PsiElement context) {
    return JavaDocUtil.findReferenceTarget(psiManager, link, context);
  }

  private static HtmlChunk classLink(@Nullable PsiClass psiClass) {
    if (psiClass == null) return HtmlChunk.empty();
    return createLink(psiClass.getQualifiedName(), psiClass.getName());
  }

  private static HtmlChunk createLink(String refText, @Nls String label) {
    HtmlChunk text = HtmlChunk.text(label).wrapWith("code");
    String link = DocumentationManagerProtocol.PSI_ELEMENT_PROTOCOL + refText;
    return HtmlChunk.link(link, text);
  }

  private static HtmlChunk generateClassDoc(@NotNull PsiElement element) {
    final DocumentationProvider documentationProvider = DocumentationManager.getProviderFromElement(element);
    String doc = StringUtil.notNullize(documentationProvider.generateDoc(element, null));
    int bodyIndex = doc.indexOf("<body>");
    if (bodyIndex >= 0) {
      doc = doc.substring(bodyIndex + 6);
    }
    return HtmlChunk.raw(doc);
  }

  private static HtmlChunk createSectionRow(HtmlChunk sectionName, @Nls String sectionContent) {
    HtmlChunk headerCell = DocumentationMarkup.SECTION_HEADER_CELL.child(sectionName.wrapWith("p"));
    HtmlChunk contentCell = DocumentationMarkup.SECTION_CONTENT_CELL.addText(sectionContent);
    return HtmlChunk.tag("tr").children(headerCell, contentCell);
  }

  private static @Nullable ExtensionPoint findExtensionPoint(PsiElement element) {
    if ((element instanceof PomTargetPsiElement || element instanceof XmlTag) &&
        DescriptorUtil.isPluginXml(element.getContainingFile())) {
      return ExtensionPoint.resolveFromDeclaration(element);
    }

    return null;
  }
}
