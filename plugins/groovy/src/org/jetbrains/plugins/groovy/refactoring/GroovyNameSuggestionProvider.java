/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.refactoring;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.statistics.JavaStatisticsManager;
import com.intellij.refactoring.rename.NameSuggestionProvider;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;

import java.util.Arrays;
import java.util.Collection;
import java.util.Set;

/**
 * @author Maxim.Medvedev
 */
public class GroovyNameSuggestionProvider implements NameSuggestionProvider {
  @Override
  public SuggestedNameInfo getSuggestedNames(final PsiElement element, PsiElement nameSuggestionContext, Set<String> result) {
    if (!(element instanceof GroovyPsiElement)) return null;
    if (element instanceof GrVariable) {
      final PsiType type = ((GrVariable)element).getTypeGroovy();
      if (type != null) {
        final String[] names =
          GroovyNameSuggestionUtil.suggestVariableNameByType(type, new DefaultGroovyVariableNameValidator(nameSuggestionContext));
        result.addAll(Arrays.asList(names));
        return new SuggestedNameInfo(names) {
          @Override
          public void nameChoosen(String name) {
            JavaStatisticsManager
              .incVariableNameUseCount(name, JavaCodeStyleManager.getInstance(element.getProject()).getVariableKind((GrVariable)element),
                                       ((GrVariable)element).getName(), type);
          }
        };
      }
    }
    return null;
  }

  @Override
  public Collection<LookupElement> completeName(PsiElement element, PsiElement nameSuggestionContext, String prefix) {
    return null;
  }
}
