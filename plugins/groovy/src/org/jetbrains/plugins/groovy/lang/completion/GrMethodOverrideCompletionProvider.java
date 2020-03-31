// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.completion;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.PatternCondition;
import com.intellij.psi.*;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.IconManager;
import com.intellij.ui.icons.RowIcon;
import com.intellij.util.ProcessingContext;
import com.intellij.util.VisibilityUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.completion.handlers.GroovyMethodOverrideHandler;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinitionBody;
import org.jetbrains.plugins.groovy.util.GroovyOverrideImplementExploreUtil;

import java.util.Collection;

import static com.intellij.patterns.PlatformPatterns.psiComment;
import static com.intellij.patterns.PlatformPatterns.psiElement;

class GrMethodOverrideCompletionProvider extends CompletionProvider<CompletionParameters> {

  private static final ElementPattern<PsiElement> PLACE = psiElement().withParent(GrTypeDefinitionBody.class).with(
    new PatternCondition<PsiElement>("Not in extends/implements clause of inner class") {
      @Override
      public boolean accepts(@NotNull PsiElement element, ProcessingContext context) {
        final GrTypeDefinition innerDefinition = PsiTreeUtil.getPrevSiblingOfType(element, GrTypeDefinition.class);
        return innerDefinition == null || innerDefinition.getContainingClass() == null || innerDefinition.getBody() != null;
      }
    }).andNot(psiComment());

  @Override
  protected void addCompletions(@NotNull CompletionParameters parameters, @NotNull ProcessingContext context, @NotNull CompletionResultSet result) {
    final PsiElement position = parameters.getPosition();
    final GrTypeDefinition currentClass = PsiTreeUtil.getParentOfType(position, GrTypeDefinition.class);

    if (currentClass != null) {
      addSuperMethods(currentClass, result, false);
      addSuperMethods(currentClass, result, true);
    }
  }

  public static void register(CompletionContributor contributor) {
    contributor.extend(CompletionType.BASIC, PLACE, new GrMethodOverrideCompletionProvider());
  }

  private static void addSuperMethods(final GrTypeDefinition psiClass, CompletionResultSet completionResultSet, boolean toImplement) {
    final Collection<CandidateInfo> candidates = GroovyOverrideImplementExploreUtil.getMethodsToOverrideImplement(psiClass, toImplement);
    for (CandidateInfo candidateInfo : candidates) {
      final PsiMethod method = (PsiMethod)candidateInfo.getElement();
      if (method.isConstructor()) continue;

      RowIcon icon = IconManager.getInstance().createRowIcon(2);
      icon.setIcon(method.getIcon(0), 0);
      icon.setIcon(toImplement ? AllIcons.Gutter.ImplementingMethod : AllIcons.Gutter.OverridingMethod, 1);

      PsiSubstitutor substitutor = candidateInfo.getSubstitutor();
      String parameters = PsiFormatUtil.formatMethod(method, substitutor, PsiFormatUtilBase.SHOW_PARAMETERS, PsiFormatUtilBase.SHOW_NAME);
      String visibility = VisibilityUtil.getVisibilityModifier(method.getModifierList());
      String modifiers = (visibility == PsiModifier.PACKAGE_LOCAL ? "" : visibility + " ");
      PsiType type = substitutor.substitute(method.getReturnType());
      String parentClassName = psiClass == null ? "" : psiClass.getName();
      String signature = modifiers + (type == null ? "" : type.getPresentableText() + " ") + method.getName();

      LookupElementBuilder lookupElement = LookupElementBuilder.create(method, signature)
        .appendTailText(parameters, false)
        .appendTailText("{...}", true)
        .withTypeText(parentClassName)
        .withIcon(icon)
        .withInsertHandler(new GroovyMethodOverrideHandler(psiClass));
      completionResultSet.addElement(lookupElement);
    }
  }
}
