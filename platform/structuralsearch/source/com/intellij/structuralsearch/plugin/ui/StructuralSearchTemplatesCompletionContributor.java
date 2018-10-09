// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.plugin.ui;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.completion.impl.CamelHumpMatcher;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.structuralsearch.MatchOptions;
import com.intellij.ui.TextFieldWithAutoCompletionListProvider;
import org.jetbrains.annotations.NotNull;

public class StructuralSearchTemplatesCompletionContributor extends CompletionContributor {
  @Override
  public void fillCompletionVariants(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
    final StructuralSearchDialog dialog = parameters.getEditor().getUserData(StructuralSearchDialog.STRUCTURAL_SEARCH);
    if (dialog == null) return;
    String prefix = TextFieldWithAutoCompletionListProvider.getCompletionPrefix(parameters);
    CompletionResultSet insensitive = result.withPrefixMatcher(new CamelHumpMatcher(prefix));
    ConfigurationManager configurationManager = ConfigurationManager.getInstance(parameters.getPosition().getProject());
    for (String configurationName: configurationManager.getAllConfigurationNames()) {
      Configuration configuration = configurationManager.findConfigurationByName(configurationName);
      if (configuration == null) continue;
      final MatchOptions matchOptions = configuration.getMatchOptions();
      LookupElementBuilder element = LookupElementBuilder.create(configuration, matchOptions.getSearchPattern())
        .withLookupString(configurationName)
        .withTailText(" (" + StringUtil.toLowerCase(matchOptions.getFileType().getName()) +
                      (configuration instanceof SearchConfiguration ? " search" : " replace") + " template)", true)
        .withCaseSensitivity(false)
        .withPresentableText(configurationName)
        .withInsertHandler((InsertionContext context, LookupElement item) -> context.setLaterRunnable(
          () -> dialog.setSearchPattern((Configuration)item.getObject())
        ));
      insensitive.addElement(element);
    }
  }
}
