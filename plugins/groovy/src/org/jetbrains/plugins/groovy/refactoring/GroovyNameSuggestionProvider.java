// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.statistics.JavaStatisticsManager;
import com.intellij.refactoring.rename.NameSuggestionProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;

import java.util.Arrays;
import java.util.Set;

/**
 * @author Maxim.Medvedev
 */
public class GroovyNameSuggestionProvider implements NameSuggestionProvider {
  @Override
  public SuggestedNameInfo getSuggestedNames(@NotNull PsiElement element,
                                             @Nullable PsiElement nameSuggestionContext,
                                             @NotNull Set<String> result) {
    if (nameSuggestionContext == null) nameSuggestionContext = element;
    if (element instanceof GrVariable && nameSuggestionContext instanceof GroovyPsiElement) {
      final PsiType type = ((GrVariable)element).getTypeGroovy();
      if (type != null) {
        final String[] names = GroovyNameSuggestionUtil
          .suggestVariableNameByType(type, new DefaultGroovyVariableNameValidator((GroovyPsiElement)nameSuggestionContext));
        result.addAll(Arrays.asList(names));
        final VariableKind kind = JavaCodeStyleManager.getInstance(element.getProject()).getVariableKind((GrVariable)element);
        final String typeText = type.getCanonicalText();
        return new SuggestedNameInfo(names) {
          @Override
          public void nameChosen(String name) {
            JavaStatisticsManager.incVariableNameUseCount(name, kind, ((GrVariable)element).getName(), typeText);
          }
        };
      }
    }
    return null;
  }
}
