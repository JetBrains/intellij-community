// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.ui.TextFieldWithAutoCompletionListProvider;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public class StructuralSearchTemplatesCompletionContributor extends CompletionContributor {
  @Override
  public void fillCompletionVariants(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
    final Editor editor = parameters.getEditor();
    final StructuralSearchDialog dialog = editor.getUserData(StructuralSearchDialog.STRUCTURAL_SEARCH_DIALOG);
    if (dialog == null) {
      final Boolean test = editor.getUserData(StructuralSearchDialog.TEST_STRUCTURAL_SEARCH_DIALOG);
      if (test == null || !test) return;
    }

    final Document document = editor.getDocument();
    final int end = parameters.getOffset();
    final int line = document.getLineNumber(end);
    final int start = document.getLineStartOffset(line);
    String shortPrefix = getCompletionPrefix(parameters);
    final CharSequence text = document.getCharsSequence();
    if (StringUtil.startsWithChar(shortPrefix, '$')) {
      Set<String> variableNames = TemplateImplUtil.parseVariableNames(dialog != null ? dialog.getSearchText() : text);
      System.out.println("variableNames = " + variableNames);
      for (String name : variableNames) {
        result.addElement(LookupElementBuilder.create('$' + name + '$')
                            .withInsertHandler(
                              (context, item) -> {
                                final int offset = context.getTailOffset();
                                if (text.length() > offset) {
                                  final char c = text.charAt(offset);
                                  if (c == '$') {
                                    document.deleteString(offset, offset + 1);
                                  }
                                }
                              })
        );
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

  @NotNull
  private static String getCompletionPrefix(CompletionParameters parameters) {
    final PsiFile file = parameters.getOriginalFile();
    final String psi = DebugUtil.psiToString(file, false);
    System.out.println("psi = " + psi);
    String text = file.getText();
    int offset = parameters.getOffset();
    return getCompletionPrefix(text, offset);
  }

  @NotNull
  private static String getCompletionPrefix(String text, int offset) {
    int i = text.lastIndexOf(' ', offset - 1) + 1;
    int j = text.lastIndexOf('\n', offset - 1) + 1;
    return text.substring(Math.max(i, j), offset);
  }
}
