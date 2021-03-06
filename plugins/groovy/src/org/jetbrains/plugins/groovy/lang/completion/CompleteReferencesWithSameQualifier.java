// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.completion;

import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.codeInsight.completion.PrefixMatcher;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public final class CompleteReferencesWithSameQualifier {
  private final GrReferenceExpression myRefExpr;
  private final PrefixMatcher myMatcher;
  private final GrExpression myQualifier;

  private CompleteReferencesWithSameQualifier(@NotNull GrReferenceExpression refExpr,
                                              @NotNull PrefixMatcher matcher,
                                              @Nullable GrExpression qualifier) {
    myRefExpr = refExpr;
    myMatcher = matcher;
    myQualifier = qualifier;
  }

  @NotNull
  public static Set<String> getVariantsWithSameQualifier(@NotNull GrReferenceExpression refExpr,
                                                         @NotNull PrefixMatcher matcher,
                                                         @Nullable GrExpression qualifier) {
    return new CompleteReferencesWithSameQualifier(refExpr, matcher, qualifier).getVariantsWithSameQualifierImpl();
  }

  private Set<String> getVariantsWithSameQualifierImpl() {
    if (myQualifier != null && myQualifier.getType() != null) return Collections.emptySet();

    final PsiElement scope = PsiTreeUtil.getParentOfType(myRefExpr, GrMember.class, PsiFile.class);
    Set<String> result = new LinkedHashSet<>();
    if (scope != null) {
      addVariantsWithSameQualifier(scope, result);
    }
    return result;
  }

  private void addVariantsWithSameQualifier(@NotNull PsiElement element, @NotNull Set<String> result) {
    if (element instanceof GrReferenceExpression && element != myRefExpr && !PsiUtil.isLValue((GroovyPsiElement)element)) {
      final GrReferenceExpression refExpr = (GrReferenceExpression)element;
      final String refName = refExpr.getReferenceName();
      if (refName != null && !result.contains(refName) && myMatcher.prefixMatches(refName)) {
        final GrExpression hisQualifier = refExpr.getQualifierExpression();
        if (hisQualifier != null && myQualifier != null) {
          if (PsiEquivalenceUtil.areElementsEquivalent(hisQualifier, myQualifier)) {
            if (refExpr.resolve() == null) {
              result.add(refName);
            }
          }
        }
        else if (hisQualifier == null && myQualifier == null) {
          if (refExpr.resolve() == null) {
            result.add(refName);
          }
        }
      }
    }

    for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
      addVariantsWithSameQualifier(child, result);
    }
  }
}
