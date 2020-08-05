// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.configmanagement.completion;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.ui.JBColor;
import com.intellij.util.ProcessingContext;
import org.editorconfig.language.psi.EditorConfigElementTypes;
import org.jetbrains.annotations.NotNull;

import static com.intellij.patterns.PlatformPatterns.psiElement;

public class EditorConfigUnsetCompletionContributor extends CompletionContributor {

  public EditorConfigUnsetCompletionContributor() {
    extend(CompletionType.BASIC,
           psiElement(EditorConfigElementTypes.IDENTIFIER)
             .withParent(psiElement(EditorConfigElementTypes.OPTION_VALUE_IDENTIFIER)),
           new MyUnsetProvider());
  }

  private static class MyUnsetProvider extends CompletionProvider<CompletionParameters> {

    @Override
    protected void addCompletions(@NotNull CompletionParameters parameters,
                                  @NotNull ProcessingContext context,
                                  @NotNull CompletionResultSet result) {
      final PsiElement position = parameters.getPosition();
      if (isAfterSeparator(position) && position.textMatches(CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED)) {
        result.addElement(LookupElementBuilder.create("unset").withItemTextForeground(JBColor.GRAY));
      }
    }

    private static boolean isAfterSeparator(@NotNull PsiElement position) {
      PsiElement parent = position.getParent();
      if (parent == null) return false;
      PsiElement prev = parent.getPrevSibling();
      if (prev instanceof PsiWhiteSpace) prev = prev.getPrevSibling();
      return prev != null && prev.getNode().getElementType() == EditorConfigElementTypes.SEPARATOR;
    }
  }
}
