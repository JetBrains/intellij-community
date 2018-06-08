// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.plugin.ui;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.impl.CamelHumpMatcher;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.ui.TextFieldWithAutoCompletionListProvider;
import org.jetbrains.annotations.NotNull;

public class StructuralSearchTemplatesCompletionContributor extends CompletionContributor {
  @Override
  public void fillCompletionVariants(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
    if (parameters.getEditor().getUserData(StructuralSearchDialog.STRUCTURAL_SEARCH) == null) return;
    String prefix = TextFieldWithAutoCompletionListProvider.getCompletionPrefix(parameters);
    CompletionResultSet insensitive = result.caseInsensitive().withPrefixMatcher(new CamelHumpMatcher(prefix));
    ConfigurationManager configurationManager = ConfigurationManager.getInstance(parameters.getPosition().getProject());
    for (String configurationName: configurationManager.getAllConfigurationNames()) {
      Configuration configuration = configurationManager.findConfigurationByName(configurationName);
      if (configuration == null) continue;
      LookupElementBuilder element = LookupElementBuilder.create(configuration, configuration.getMatchOptions().getSearchPattern())
                                                         .withLookupString(configurationName)
                                                         .withPresentableText(configurationName);
      insensitive.addElement(element);
    }
  }
}
