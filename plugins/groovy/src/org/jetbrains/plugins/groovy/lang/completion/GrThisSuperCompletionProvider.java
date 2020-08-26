// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.completion;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

class GrThisSuperCompletionProvider extends CompletionProvider<CompletionParameters> {
  private static final ElementPattern<PsiElement> AFTER_DOT = PlatformPatterns.psiElement().afterLeaf(".").withParent(GrReferenceExpression.class);
  private static final String[] THIS_SUPER = {"this", "super"};

  public static void register(CompletionContributor contributor) {
    contributor.extend(CompletionType.BASIC, AFTER_DOT, new GrThisSuperCompletionProvider());
  }

  @Override
  protected void addCompletions(@NotNull CompletionParameters parameters,
                                @NotNull ProcessingContext context,
                                @NotNull CompletionResultSet result) {
    final PsiElement position = parameters.getPosition();

    assert position.getParent() instanceof GrReferenceExpression;
    final GrReferenceExpression refExpr = ((GrReferenceExpression)position.getParent());
    final GrExpression qualifier = refExpr.getQualifierExpression();
    if (!(qualifier instanceof GrReferenceExpression)) return;

    GrReferenceExpression referenceExpression = (GrReferenceExpression)qualifier;
    final PsiElement resolved = referenceExpression.resolve();
    if (!(resolved instanceof PsiClass)) return;

    if (parameters.getInvocationCount() > 0 &&
        CompletionUtil.shouldShowFeature(parameters, JavaCompletionFeatures.GLOBAL_MEMBER_NAME)) {
      final String shortcut = KeymapUtil.getFirstKeyboardShortcutText(ActionManager.getInstance().getAction(IdeActions.ACTION_CODE_COMPLETION));
      if (StringUtil.isNotEmpty(shortcut)) {
        result.addLookupAdvertisement(GroovyBundle.message("this.super.completion.advertisement", shortcut));
      }
    }

    if (!PsiUtil.hasEnclosingInstanceInScope((PsiClass)resolved, position, false)) return;

    for (String keyword : THIS_SUPER) {
      result.addElement(LookupElementBuilder.create(keyword));
    }
  }
}
