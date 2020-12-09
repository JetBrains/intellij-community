// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.plugin.ui;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.completion.impl.CamelHumpMatcher;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.template.impl.TemplateImplUtil;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.TextFieldWithAutoCompletionListProvider;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public class StructuralSearchTemplatesCompletionContributor extends CompletionContributor {
  @Override
  public void fillCompletionVariants(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
    final StructuralSearchDialog dialog = parameters.getEditor().getUserData(StructuralSearchDialog.STRUCTURAL_SEARCH_DIALOG);
    if (dialog == null) {
      final Boolean test = parameters.getEditor().getUserData(StructuralSearchDialog.TEST_STRUCTURAL_SEARCH_DIALOG);
      if (test == null || !test) return;
    }

    final Document document = parameters.getEditor().getDocument();
    final int end = parameters.getOffset();
    final int line = document.getLineNumber(end);
    final int start = document.getLineStartOffset(line);
    String shortPrefix = TextFieldWithAutoCompletionListProvider.getCompletionPrefix(parameters);
    final CharSequence text = document.getCharsSequence();
    if (StringUtil.startsWithChar(shortPrefix, '$')) {
      shortPrefix = shortPrefix.substring(1);
      Set<String> variableNames = TemplateImplUtil.parseVariableNames(text);
      for (String name : variableNames) {
        if (name.startsWith(shortPrefix) && !name.equals(shortPrefix)) {
          result.addElement(LookupElementBuilder.create('$' + name + '$')
          .withInsertHandler((context, item) -> {
            final int offset = context.getTailOffset();
            if (text.length() > offset + 1) {
              final char c = text.charAt(offset);
              if (c == '$') {
                document.deleteString(offset, offset + 1);
              }
            }
          }));
        }
      }
    }
    final String prefix = parameters.isExtendedCompletion()
                          ? shortPrefix
                          : text.subSequence(start, end).toString();
    if (StringUtil.containsChar(prefix, '$')) {
      return;
    }
    result.runRemainingContributors(parameters, cr -> {
      if (cr.getLookupElement().getObject() instanceof String) return;
      result.passResult(cr);
    });
    CompletionResultSet insensitive = result.withPrefixMatcher(new CamelHumpMatcher(prefix));
    ConfigurationManager configurationManager = ConfigurationManager.getInstance(parameters.getPosition().getProject());
    for (Configuration configuration: configurationManager.getAllConfigurations()) {

        LookupElementBuilder element = LookupElementBuilder.create(configuration, configuration.getMatchOptions().getSearchPattern())
          .withLookupString(configuration.getName())
          .withTypeText(configuration.getTypeText(), true)
          .withIcon(configuration.getIcon())
          .withCaseSensitivity(false)
          .withPresentableText(configuration.getName());

        if (dialog != null)
          element = element.withInsertHandler((InsertionContext context, LookupElement item) -> context.setLaterRunnable(
            () -> dialog.loadConfiguration((Configuration)item.getObject())
          ));
        insensitive.addElement(element);
    }
  }
}
