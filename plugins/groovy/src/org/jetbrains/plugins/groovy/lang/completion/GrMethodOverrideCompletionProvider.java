/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.completion;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.generation.OverrideImplementExploreUtil;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.*;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.RowIcon;
import com.intellij.util.ProcessingContext;
import com.intellij.util.VisibilityUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.completion.handlers.GroovyMethodOverrideHandler;

import javax.swing.*;
import java.util.Collection;

import static com.intellij.patterns.PlatformPatterns.psiElement;

/**
 * Created by Arasthel on 29/01/15.
 */

class GrMethodOverrideCompletionProvider extends CompletionProvider<CompletionParameters> {

  @Override
  protected void addCompletions(@NotNull CompletionParameters parameters, ProcessingContext context, @NotNull CompletionResultSet result) {
    PsiElement position = parameters.getPosition();

    if(psiElement()
            .inside(PsiClass.class)
            .accepts(position)) {

      PsiClass currentClass = PsiTreeUtil.getParentOfType(position, PsiClass.class);

      if (currentClass != null) {
        addSuperMethods(currentClass, result, false);
        addSuperMethods(currentClass, result, true);
      }
    }
  }

  public static void register(CompletionContributor contributor) {
    contributor.extend(CompletionType.BASIC, PlatformPatterns.psiElement(PsiElement.class), new GrMethodOverrideCompletionProvider());
  }

  private void addSuperMethods(final PsiClass psiClass, CompletionResultSet completionResultSet, boolean implement) {
    Collection<CandidateInfo> candidates = OverrideImplementExploreUtil.getMethodsToOverrideImplement(psiClass, implement);
    for(CandidateInfo candidateInfo : candidates) {
      final PsiMethod method = (PsiMethod) candidateInfo.getElement();
      PsiSubstitutor substitutor = candidateInfo.getSubstitutor();

      RowIcon icon = new RowIcon(2);
      icon.setIcon(method.getIcon(0), 0);
      icon.setIcon(implement ? AllIcons.Gutter.ImplementingMethod : AllIcons.Gutter.OverridingMethod, 1);

      if(!method.isConstructor()) {
        completionResultSet.addElement(createLookUpElement(method, substitutor, psiClass, icon, new GroovyMethodOverrideHandler(psiClass)));
      }
    }
  }

  private LookupElementBuilder createLookUpElement(final PsiMethod method, PsiSubstitutor substitutor, PsiClass currentClass, Icon icon,
                                                   InsertHandler<LookupElement> insertHandler) {
    String parameters = PsiFormatUtil.formatMethod(method, substitutor, PsiFormatUtilBase.SHOW_PARAMETERS, PsiFormatUtilBase.SHOW_NAME);

    String visibility = VisibilityUtil.getVisibilityModifier(method.getModifierList());
    String modifiers = (visibility == PsiModifier.PACKAGE_LOCAL ? "" : visibility + " ");

    PsiType type = substitutor.substitute(method.getReturnType());

    String parentClassName = currentClass == null ? "" : ((PsiNamedElement) currentClass).getName();

    String signature = modifiers + (type == null ? "" : type.getPresentableText() + " ") + method.getName();

    LookupElementBuilder elementBuilder = LookupElementBuilder.create(method, signature)
      .withLookupString(signature)
      .appendTailText(parameters, false)
      .appendTailText("{...}", true)
      .withTypeText(parentClassName)
      .withIcon(icon)
      .withInsertHandler(insertHandler);

    return elementBuilder;
  }
}
