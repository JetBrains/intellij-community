// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.debugger;

import com.intellij.debugger.engine.evaluation.CodeFragmentKind;
import com.intellij.debugger.engine.evaluation.TextWithImports;
import com.intellij.debugger.engine.evaluation.TextWithImportsImpl;
import com.intellij.debugger.impl.EditorTextProvider;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiEnumConstant;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringUtil;

/**
 * @author Maxim.Medvedev
 */
public final class GroovyEditorTextProvider implements EditorTextProvider {
  @Override
  public TextWithImports getEditorText(PsiElement elementAtCaret) {
    String result = "";
    PsiElement element = findExpressionInner(elementAtCaret, true);
    if (element != null) {
      if (element instanceof GrReferenceExpression reference) {
        if (reference.getQualifier() == null) {
          final PsiElement resolved = reference.resolve();
          if (resolved instanceof PsiEnumConstant enumConstant) {
            final PsiClass enumClass = enumConstant.getContainingClass();
            if (enumClass != null) {
              result = enumClass.getName() + "." + enumConstant.getName();
            }
          }
        }
      }
      if (result.isEmpty()) {
        result = element.getText();
      }
    }
    return new TextWithImportsImpl(CodeFragmentKind.EXPRESSION, result);
  }

  private static @Nullable PsiElement findExpressionInner(PsiElement element, boolean allowMethodCalls) {
    PsiElement parent = element.getParent();
    if (parent instanceof GrVariable && element == ((GrVariable)parent).getNameIdentifierGroovy()) {
      return element;
    }
    else if (parent instanceof GrReferenceExpression) {
      final PsiElement pparent = parent.getParent();
      if (pparent instanceof GrCall) {
        parent = pparent;
      }
      if (allowMethodCalls || !GroovyRefactoringUtil.hasSideEffect((GroovyPsiElement)parent)) {
        return parent;
      }
    }

    return null;
  }

  @Override
  public Pair<PsiElement, TextRange> findExpression(PsiElement element, boolean allowMethodCalls) {
    PsiElement expression = findExpressionInner(element, allowMethodCalls);
    if (expression == null) return null;
    return Pair.create(expression, expression.getTextRange());
  }
}
