// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.references.extensions;

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
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomFileElement;
import com.intellij.util.xml.DomUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
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
      .append(
        HtmlChunk.fragment(
          epBeanClassLinkAndFields(extensionPoint),
          epImplementationClassLink(extensionPoint),
          HtmlChunk.hr(),
          resourceLinks(extensionPoint)
        ).wrapWith(DocumentationMarkup.CONTENT_ELEMENT)
      )
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

  private static @NotNull HtmlChunk epBeanClassLinkAndFields(ExtensionPoint extensionPoint) {
    final PsiClass beanClass = extensionPoint.getBeanClass().getValue();
    if (beanClass != null) {
      return HtmlChunk.fragment(
        sectionHeader(DevKitBundle.message("extension.point.documentation.bean.section")),
        HtmlChunk.raw(DevKitBundle.message("extension.point.documentation.bean.details", classLink(beanClass))).wrapWith(HtmlChunk.p()),
        epBeanFields(beanClass)
      );
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
        final String requiredText = switch (required) {
          case REQUIRED -> " " + DevKitBundle.message("extension.point.documentation.field.required.suffix");
          case REQUIRED_ALLOW_EMPTY -> " " + DevKitBundle.message("extension.point.documentation.field.required.can.be.empty.suffix");
          default -> "";
        };
        final String initializer = field.getInitializer() != null ? " = " + field.getInitializer().getText() : "";
        bindingRows.append(createSectionRow(hyperLink, typeText + requiredText + initializer));
      }
    });

    if (!bindingRows.isEmpty()) {
      return HtmlChunk.fragment(
        HtmlChunk.text(DevKitBundle.message("extension.point.documentation.field.bindings.section")).bold().wrapWith(HtmlChunk.p()),
        bindingRows.wrapWith(DocumentationMarkup.SECTIONS_TABLE)
      );
    }
    return HtmlChunk.empty();
  }

  private static @NotNull HtmlChunk epImplementationClassLink(ExtensionPoint extensionPoint) {
    PsiClass implementationClass = extensionPoint.getExtensionPointClass();
    if (implementationClass == null) return HtmlChunk.empty();
    return HtmlChunk.fragment(
      sectionHeader(DevKitBundle.message("extension.point.documentation.implementation.section")),
      HtmlChunk.raw(
        implementationClass.isInterface() ?
        DevKitBundle.message("extension.point.documentation.implementation.details.interface", classLink(implementationClass)) :
        DevKitBundle.message("extension.point.documentation.implementation.details.class", classLink(implementationClass))
      ).wrapWith(HtmlChunk.p())
    );
  }

  private static @NotNull HtmlChunk sectionHeader(@Nls String title) {
    return HtmlChunk.text(title).wrapWith("h4");
  }

  private static @NotNull HtmlChunk resourceLinks(ExtensionPoint extensionPoint) {
    return HtmlChunk.fragment(
      HtmlChunk.text(DevKitBundle.message("extension.point.documentation.resources.section")),
      HtmlChunk.ul().children(
        linkItem(
          "https://jb.gg/ipe?extensions=" + extensionPoint.getEffectiveQualifiedName(),
          DevKitBundle.message("extension.point.documentation.link.platform.explorer")),
        linkItem(
          "https://plugins.jetbrains.com/docs/intellij/plugin-extension-points.html",
          DevKitBundle.message("extension.point.documentation.link.extension.points")),
        linkItem(
          "https://plugins.jetbrains.com/docs/intellij/plugin-extensions.html",
          DevKitBundle.message("extension.point.documentation.link.extensions"))
      )
    );
  }

  private static @NotNull HtmlChunk linkItem(@NonNls String target, @Nls String text) {
    return HtmlChunk.li().child(HtmlChunk.link(target, text));
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
