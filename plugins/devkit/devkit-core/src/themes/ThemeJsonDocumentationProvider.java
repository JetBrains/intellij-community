// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.themes;

import com.intellij.codeInsight.javadoc.JavaDocInfoGenerator;
import com.intellij.ide.ui.UIThemeMetadata;
import com.intellij.json.psi.JsonProperty;
import com.intellij.lang.documentation.AbstractDocumentationProvider;
import com.intellij.lang.documentation.DocumentationMarkup;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public class ThemeJsonDocumentationProvider extends AbstractDocumentationProvider {

  @Nullable
  @Override
  public String getQuickNavigateInfo(PsiElement element, PsiElement originalElement) {
    final Pair<UIThemeMetadata, UIThemeMetadata.UIKeyMetadata> resolve = resolve(element);
    if (resolve == null) return null;

    return "<b>" + resolve.second.getKey() + "</b> [" + resolve.first.getName() + "]\n" +
           StringUtil.notNullize(resolve.second.getDescription());
  }

  @Override
  public String generateDoc(PsiElement element, @Nullable PsiElement originalElement) {
    final Pair<UIThemeMetadata, UIThemeMetadata.UIKeyMetadata> resolve = resolve(element);
    if (resolve == null) return null;

    final UIThemeMetadata uiThemeMetadata = resolve.first;
    final UIThemeMetadata.UIKeyMetadata uiKeyMetadata = resolve.second;

    StringBuilder sb = new StringBuilder(DocumentationMarkup.DEFINITION_START);
    sb.append("<b>").append(uiKeyMetadata.getKey()).append("</b><br>");
    sb.append("[").append(uiThemeMetadata.getName()).append("] - ");
    sb.append("[").append(uiThemeMetadata.getPluginId()).append("]");
    sb.append(DocumentationMarkup.DEFINITION_END);

    sb.append(DocumentationMarkup.CONTENT_START);
    if (uiKeyMetadata.isDeprecated()) {
      sb.append("<font color='#").append(ColorUtil.toHex(JBColor.RED)).append("'><b>Deprecated</b></font><br>");
    }

    sb.append(StringUtil.notNullize(uiKeyMetadata.getDescription(), "(no description)"));
    sb.append("<br><br>");
    sb.append(DocumentationMarkup.CONTENT_END);

    sb.append(DocumentationMarkup.SECTIONS_START);

    final String source = uiKeyMetadata.getSource();
    if (source != null) {
      appendSection(sb, "Source", () -> {
        final PsiClassType type = JavaPsiFacade.getElementFactory(element.getProject()).createTypeByFQClassName(source);

        StringBuilder typeBuilder = new StringBuilder();
        JavaDocInfoGenerator.generateType(typeBuilder, type, element);
        return typeBuilder.toString();
      });
    }

    final String since = uiKeyMetadata.getSince();
    if (since != null) {
      appendSection(sb, "Since", since);
    }

    sb.append(DocumentationMarkup.SECTIONS_END);
    return sb.toString();
  }

  private static void appendSection(StringBuilder sb, String sectionName, String sectionContent) {
    appendSection(sb, sectionName, () -> sectionContent);
  }

  private static void appendSection(StringBuilder sb, String sectionName, Supplier<String> content) {
    sb.append(DocumentationMarkup.SECTION_HEADER_START).append(sectionName).append(":")
      .append(DocumentationMarkup.SECTION_SEPARATOR);
    sb.append(content.get());
    sb.append(DocumentationMarkup.SECTION_END);
  }

  @Nullable
  private static Pair<UIThemeMetadata, UIThemeMetadata.UIKeyMetadata> resolve(PsiElement element) {
    if (!(element instanceof JsonProperty)) return null;
    if (!ThemeJsonUtil.isThemeFilename(element.getContainingFile().getName())) return null;

    return ThemeJsonUtil.findMetadata((JsonProperty)element);
  }
}