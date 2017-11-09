// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.completion;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.extensions.LoadingOrder;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.PatternCondition;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.patterns.XmlPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ProcessingContext;
import com.intellij.util.xml.DomManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.dom.Extension;

import static com.intellij.patterns.PlatformPatterns.psiElement;

public class ExtensionOrderKeywordCompletionContributor extends CompletionContributor {
  private static final LookupElementBuilder KEYWORD_VARIANT_FIRST = LookupElementBuilder.create(LoadingOrder.FIRST_STR);
  private static final LookupElementBuilder KEYWORD_VARIANT_LAST = LookupElementBuilder.create(LoadingOrder.LAST_STR);
  private static final LookupElementBuilder KEYWORD_VARIANT_BEFORE = LookupElementBuilder.create(LoadingOrder.BEFORE_STR.trim())
    .withInsertHandler(new AddSpaceInsertHandler(true));
  private static final LookupElementBuilder KEYWORD_VARIANT_AFTER = LookupElementBuilder.create(LoadingOrder.AFTER_STR.trim())
    .withInsertHandler(new AddSpaceInsertHandler(true));

  public ExtensionOrderKeywordCompletionContributor() {
    extend(CompletionType.BASIC, getCapture(), new CompletionProvider<CompletionParameters>() {
      @Override
      protected void addCompletions(@NotNull CompletionParameters parameters,
                                    ProcessingContext context,
                                    @NotNull CompletionResultSet result) {
        String prefix = getCompletionPrefix(parameters);
        if (!shouldProposeKeywordsAfterPrefix(prefix)) {
          return;
        }
        if (shouldProposeFirstLastKeywordsAfterPrefix(prefix)) {
          result.addElement(KEYWORD_VARIANT_FIRST);
          result.addElement(KEYWORD_VARIANT_LAST);
        }
        result.addElement(KEYWORD_VARIANT_BEFORE);
        result.addElement(KEYWORD_VARIANT_AFTER);
      }
    });
  }

  @NotNull
  private static PsiElementPattern.Capture<PsiElement> getCapture() {
    //TODO write a method for attribute value in XmlPatterns
    return psiElement().inside(
      XmlPatterns.xmlAttributeValue("order").inside(
        XmlPatterns.xmlTag().with(new PatternCondition<XmlTag>("extension tag") {
          @Override
          public boolean accepts(@NotNull XmlTag tag, ProcessingContext context) {
            Project project = tag.getProject();
            DomManager domManager = DomManager.getDomManager(project);
            return domManager.getDomElement(tag) instanceof Extension;
          }
        })));
  }

  @NotNull
  private static String getCompletionPrefix(@NotNull CompletionParameters parameters) {
    XmlElement position = (XmlElement)parameters.getPosition();
    int startOffset = position.getTextOffset();
    int endOffset = parameters.getOffset();
    Document document = parameters.getEditor().getDocument();
    return document.getText(new TextRange(startOffset, endOffset));
  }

  @NotNull
  private static String getPrefixLastPart(@NotNull String prefix) {
    String lastPart = StringUtil.substringAfterLast(prefix, LoadingOrder.ORDER_RULE_SEPARATOR);
    if (lastPart == null) {
      lastPart = prefix;
    }
    lastPart = StringUtil.trimLeading(lastPart);
    return lastPart;
  }

  private static boolean shouldProposeKeywordsAfterPrefix(@NotNull String prefix) {
    return !getPrefixLastPart(prefix).contains(" "); // propose keywords if there's only a single word (or empty prefix)
  }

  private static boolean shouldProposeFirstLastKeywordsAfterPrefix(@NotNull String prefix) {
    String[] parts = prefix.split(LoadingOrder.ORDER_RULE_SEPARATOR);
    for (String part : parts) {
      if (part.trim().equalsIgnoreCase(LoadingOrder.FIRST_STR) || part.trim().equalsIgnoreCase(LoadingOrder.LAST_STR)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public void fillCompletionVariants(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
    String prefix = result.getPrefixMatcher().getPrefix();
    if (prefix.endsWith(LoadingOrder.ORDER_RULE_SEPARATOR)) {
      result = result.withPrefixMatcher(""); // keywords should be proposed after comma even without space
    }
    else {
      result = result.withPrefixMatcher(getPrefixLastPart(prefix));
    }

    super.fillCompletionVariants(parameters, result);
  }
}
