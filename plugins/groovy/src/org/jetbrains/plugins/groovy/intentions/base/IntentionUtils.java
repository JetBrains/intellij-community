package org.jetbrains.plugins.groovy.intentions.base;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import com.intellij.util.IncorrectOperationException;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiElement;

/**
 * User: Dmitry.Krasilschikov
 * Date: 13.11.2007
 */
public class IntentionUtils {

  public static void replaceExpression(@NotNull String newExpression,
                                          @NotNull GrExpression expression)
      throws IncorrectOperationException {
    final PsiManager mgr = expression.getManager();
    final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(expression.getProject());
    final GrExpression newCall =
        factory.createExpressionFromText(newExpression);
    final PsiElement insertedElement = expression.replaceWithExpression(newCall, true);
    //  final CodeStyleManager codeStyleManager = mgr.getCodeStyleManager();
    // codeStyleManager.reformat(insertedElement);
  }

  public static GrStatement replaceStatement(
      @NonNls @NotNull String newStatement,
      @NonNls @NotNull GrStatement statement)
      throws IncorrectOperationException {
    final PsiManager mgr = statement.getManager();
    final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(statement.getProject());
    final GrStatement newCall =
        (GrStatement) factory.createTopElementFromText(newStatement);
    return statement.replaceWithStatement(newCall);
    //  final CodeStyleManager codeStyleManager = mgr.getCodeStyleManager();
    // codeStyleManager.reformat(insertedElement);
  }
}
