package org.jetbrains.javafx.refactoring.surround;

import com.intellij.lang.surroundWith.SurroundDescriptor;
import com.intellij.lang.surroundWith.Surrounder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.javafx.lang.psi.JavaFxValueExpression;
import org.jetbrains.javafx.refactoring.JavaFxRefactoringUtil;
import org.jetbrains.javafx.refactoring.surround.surrounders.expressions.JavaFxWithAsSurrounder;
import org.jetbrains.javafx.refactoring.surround.surrounders.expressions.JavaFxWithNotInstanceofSurrounder;
import org.jetbrains.javafx.refactoring.surround.surrounders.expressions.JavaFxWithNotSurrounder;
import org.jetbrains.javafx.refactoring.surround.surrounders.expressions.JavaFxWithParenthesesSurrounder;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public class JavaFxExpressionSurroundDescriptor implements SurroundDescriptor {
  private static final Surrounder[] SURROUNDERS =
    {new JavaFxWithParenthesesSurrounder(), new JavaFxWithNotSurrounder(), new JavaFxWithAsSurrounder(),
      new JavaFxWithNotInstanceofSurrounder()};

  @NotNull
  @Override
  public PsiElement[] getElementsToSurround(PsiFile file, int startOffset, int endOffset) {
    final JavaFxValueExpression expression = JavaFxRefactoringUtil.findExpressionInRange(file, startOffset, endOffset);
    return new PsiElement[]{expression};
  }

  @NotNull
  @Override
  public Surrounder[] getSurrounders() {
    return SURROUNDERS;
  }
}
