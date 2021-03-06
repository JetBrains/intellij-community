// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.themes;

import com.intellij.codeInsight.javadoc.JavaDocInfoGenerator;
import com.intellij.ide.ui.UIThemeMetadata;
import com.intellij.json.psi.JsonProperty;
import com.intellij.lang.documentation.AbstractDocumentationProvider;
import com.intellij.lang.documentation.DocumentationMarkup;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;

import java.util.function.Supplier;

public class ThemeJsonDocumentationProvider extends AbstractDocumentationProvider {

  @Nullable
  @Override
  public String getQuickNavigateInfo(PsiElement element, PsiElement originalElement) {
    final Pair<UIThemeMetadata, UIThemeMetadata.UIKeyMetadata> resolve = resolve(element);
    if (resolve == null) return null;

    return new HtmlBuilder()
      .append(HtmlChunk.text(resolve.second.getKey()).bold())
      .nbsp()
      .append("[" + resolve.first.getName() + "]")
      .br()
      .append(StringUtil.notNullize(resolve.second.getDescription()))
      .toString();
  }

  @Override
  public String generateDoc(PsiElement element, @Nullable PsiElement originalElement) {
    final Pair<UIThemeMetadata, UIThemeMetadata.UIKeyMetadata> resolve = resolve(element);
    if (resolve == null) return null;

    final UIThemeMetadata uiThemeMetadata = resolve.first;
    final UIThemeMetadata.UIKeyMetadata uiKeyMetadata = resolve.second;

    HtmlBuilder builder = new HtmlBuilder();

    HtmlBuilder definitionBuilder = new HtmlBuilder();
    definitionBuilder.append(HtmlChunk.text(uiKeyMetadata.getKey()).bold());
    definitionBuilder.br().append("[").append(uiThemeMetadata.getName()).append("] - ");
    definitionBuilder.append("[").append(uiThemeMetadata.getPluginId()).append("]");
    HtmlChunk.Element definition = definitionBuilder.wrapWith("pre").wrapWith(DocumentationMarkup.DEFINITION_ELEMENT);
    builder.append(definition);


    HtmlBuilder contentBuilder = new HtmlBuilder();
    if (uiKeyMetadata.isDeprecated()) {
      contentBuilder.append(
        HtmlChunk.text(DevKitBundle.message("theme.json.documentation.key.deprecated"))
          .bold()
          .wrapWith(HtmlChunk.font("#" + ColorUtil.toHex(JBColor.RED))))
        .append(HtmlChunk.br());
    }
    contentBuilder.append(HtmlChunk.text(StringUtil.notNullize(uiKeyMetadata.getDescription(),
                                                               DevKitBundle.message("theme.json.documentation.key.no.description"))));
    contentBuilder.append(HtmlChunk.br()).append(HtmlChunk.br());
    final HtmlChunk.Element content = contentBuilder.wrapWith(DocumentationMarkup.CONTENT_ELEMENT);
    builder.append(content);


    HtmlBuilder sectionsBuilder = new HtmlBuilder();

    final String source = uiKeyMetadata.getSource();
    if (source != null) {
      appendSection(sectionsBuilder, DevKitBundle.message("theme.json.documentation.section.source.title"), () -> {
        final PsiClassType type = JavaPsiFacade.getElementFactory(element.getProject()).createTypeByFQClassName(source);

        StringBuilder typeBuilder = new StringBuilder();
        JavaDocInfoGenerator.generateType(typeBuilder, type, element);
        return typeBuilder.toString(); //NON-NLS
      });
    }

    final String since = uiKeyMetadata.getSince();
    if (since != null) {
      appendSection(sectionsBuilder, DevKitBundle.message("theme.json.documentation.section.since.title"), since);
    }
    final HtmlChunk.Element sections = sectionsBuilder.wrapWith(DocumentationMarkup.SECTIONS_TABLE);
    builder.append(sections);

    return builder.toString();
  }

  private static void appendSection(HtmlBuilder builder, @Nls String sectionName, @Nls String sectionContent) {
    appendSection(builder, sectionName, () -> sectionContent);
  }

  private static void appendSection(HtmlBuilder builder, @Nls String sectionName, Supplier<@Nls String> content) {
    HtmlChunk headerCell = DocumentationMarkup.SECTION_HEADER_CELL.child(HtmlChunk.text(sectionName).wrapWith("p"));
    HtmlChunk contentCell = DocumentationMarkup.SECTION_CONTENT_CELL.addText(content.get());
    builder.append(HtmlChunk.tag("tr").children(headerCell, contentCell));
  }

  @Nullable
  private static Pair<UIThemeMetadata, UIThemeMetadata.UIKeyMetadata> resolve(PsiElement element) {
    if (!(element instanceof JsonProperty)) return null;
    if (!ThemeJsonUtil.isThemeFilename(element.getContainingFile().getName())) return null;

    return ThemeJsonUtil.findMetadata((JsonProperty)element);
  }
}