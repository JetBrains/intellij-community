package org.jetbrains.plugins.groovy.lang.surroundWith.surrounders;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.plugins.groovy.lang.psi.*;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.GrTopStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;

/**
 * User: Dmitry.Krasilschikov
 * Date: 22.05.2007
 */
public class GroovySurrounderByClosure extends GroovyManyStatementsSurrounder {
  private static final Key<GroovyResolveResult> REF_RESOLVE_RESULT_KEY = Key.create("REF_RESOLVE_RESULT");

  public String getTemplateDescription() {
    return "{ -> ... }.call()";
  }

  protected GroovyPsiElement doSurroundElements(PsiElement[] elements) throws IncorrectOperationException {
    for (PsiElement element : elements) {
      if (element instanceof GroovyPsiElement) {
        ((GroovyPsiElement) element).accept(new MyMemoizingVisitor());
      }
    }

    GroovyElementFactory factory = GroovyElementFactory.getInstance(elements[0].getProject());
    final GrMethodCallExpression call = (GrMethodCallExpression) factory.createTopElementFromText("{ -> \n }.call()");
    final GrClosableBlock closure = (GrClosableBlock) ((GrReferenceExpression) call.getInvokedExpression()).getQualifierExpression();
    addStatements(closure, elements);
    return call;
  }

  protected TextRange getSurroundSelectionRange(GroovyPsiElement element) {
    element.accept(new MyRestoringVisitor());
    assert element instanceof GrMethodCallExpression;

    final int offset = element.getTextRange().getEndOffset();
    return new TextRange(offset, offset);
  }

  private static class MyMemoizingVisitor extends GroovyRecursiveElementVisitor {
    public void visitReferenceExpression(GrReferenceExpression ref) {
      if (ref.getQualifierExpression() == null) { //only unqualified references could change their targets
        final GroovyResolveResult resolveResult = ref.advancedResolve();
        ref.putCopyableUserData(REF_RESOLVE_RESULT_KEY, resolveResult);
      }
      super.visitReferenceExpression(ref);
    }
  }

  private static class MyRestoringVisitor extends GroovyRecursiveElementVisitor {
    public void visitReferenceExpression(GrReferenceExpression ref) {
      final GroovyResolveResult oldResult = ref.getCopyableUserData(REF_RESOLVE_RESULT_KEY);
      if (oldResult != null) {
        assert ref.getQualifierExpression() == null;
        final GroovyResolveResult newResult = ref.advancedResolve();
        final PsiElement oldElement = oldResult.getElement();
        final PsiElement newElement = newResult.getElement();
        if (oldElement != newElement || oldResult.getCurrentFileResolveContext() != newResult.getCurrentFileResolveContext()) {
          final GrReferenceExpression qualifier = GroovyElementFactory.getInstance(ref.getProject()).createReferenceExpressionFromText("owner");
          ref.setQualifierExpression(qualifier);
        }
      }
      super.visitReferenceExpression(ref);
    }
  }
}