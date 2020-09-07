// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.surroundWith;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;

public class SurrounderByClosure extends GroovyManyStatementsSurrounder {
  private static final Key<GroovyResolveResult> REF_RESOLVE_RESULT_KEY = Key.create("REF_RESOLVE_RESULT");

  @Override
  public String getTemplateDescription() {
    //noinspection DialogTitleCapitalization
    return GroovyBundle.message("surround.with.closure");
  }

  @Override
  protected GroovyPsiElement doSurroundElements(PsiElement[] elements, PsiElement context) throws IncorrectOperationException {
    for (PsiElement element : elements) {
      if (element instanceof GroovyPsiElement) {
        ((GroovyPsiElement) element).accept(new MyMemoizingVisitor());
      }
    }

    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(elements[0].getProject());
    final GrMethodCallExpression call = (GrMethodCallExpression) factory.createExpressionFromText("{ -> \n}.call()", context);
    final GrClosableBlock closure = (GrClosableBlock) ((GrReferenceExpression) call.getInvokedExpression()).getQualifierExpression();
    addStatements(closure, elements);
    return call;
  }

  @Override
  protected TextRange getSurroundSelectionRange(GroovyPsiElement element) {
    element.accept(new MyRestoringVisitor());
    assert element instanceof GrMethodCallExpression;

    final int offset = element.getTextRange().getEndOffset();
    return new TextRange(offset, offset);
  }

  private static class MyMemoizingVisitor extends GroovyRecursiveElementVisitor {
    @Override
    public void visitReferenceExpression(@NotNull GrReferenceExpression ref) {
      if (ref.getQualifierExpression() == null) { //only unqualified references could change their targets
        final GroovyResolveResult resolveResult = ref.advancedResolve();
        ref.putCopyableUserData(REF_RESOLVE_RESULT_KEY, resolveResult);
      }
      super.visitReferenceExpression(ref);
    }
  }

  private static class MyRestoringVisitor extends GroovyRecursiveElementVisitor {
    @Override
    public void visitReferenceExpression(@NotNull GrReferenceExpression ref) {
      final GroovyResolveResult oldResult = ref.getCopyableUserData(REF_RESOLVE_RESULT_KEY);
      if (oldResult != null) {
        assert ref.getQualifierExpression() == null;
        final GroovyResolveResult newResult = ref.advancedResolve();
        final PsiElement oldElement = oldResult.getElement();
        final PsiElement newElement = newResult.getElement();
        if (!ref.getManager().areElementsEquivalent(oldElement, newElement) ||
            oldResult.getCurrentFileResolveContext() != newResult.getCurrentFileResolveContext()) {
          final GrReferenceExpression qualifier = GroovyPsiElementFactory.getInstance(ref.getProject()).createReferenceExpressionFromText("owner");
          ref.setQualifier(qualifier);
        }
      }
      super.visitReferenceExpression(ref);
    }
  }
}