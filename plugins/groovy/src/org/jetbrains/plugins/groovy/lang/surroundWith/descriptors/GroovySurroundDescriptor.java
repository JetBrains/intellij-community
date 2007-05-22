package org.jetbrains.plugins.groovy.lang.surroundWith.descriptors;

import com.intellij.lang.surroundWith.SurroundDescriptor;
import com.intellij.lang.surroundWith.Surrounder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.surroundWith.surrounders.surroundersImpl.expressions.GroovyWithBracketsSurrounder;
import org.jetbrains.plugins.groovy.lang.surroundWith.surrounders.surroundersImpl.blocks.open.GroovyWithTryCatchSurrounder;
import org.jetbrains.plugins.groovy.lang.surroundWith.surrounders.surroundersImpl.blocks.open.GroovyWithTryFinallySurrounder;
import org.jetbrains.plugins.groovy.lang.surroundWith.surrounders.surroundersImpl.blocks.open.GroovyWithTryCatchFinallySurrounder;
import org.jetbrains.plugins.groovy.lang.surroundWith.surrounders.GroovySurrounderByParametrizedClosure;
import org.jetbrains.plugins.groovy.lang.surroundWith.surrounders.GroovySurrounderByClosure;

import java.util.List;
import java.util.ArrayList;

/**
 * User: Dmitry.Krasilschikov
 * Date: 22.05.2007
 */
public class GroovySurroundDescriptor implements SurroundDescriptor {
  @NotNull
  public PsiElement[] getElementsToSurround(PsiFile file, int startOffset, int endOffset) {
    GrStatement[] statements = findStatementsInRange(file, startOffset, endOffset);

    if (statements == null) return PsiElement.EMPTY_ARRAY;
    return statements;
  }

  @Nullable
  private GrStatement[] findStatementsInRange(PsiFile file, int startOffset, int endOffset) {

    PsiElement element1 = file.findElementAt(startOffset);
    PsiElement element2 = file.findElementAt(endOffset - 1);

    int endOffsetLocal = endOffset;
    int startOffsetLocal = startOffset;

    if (element1 instanceof PsiWhiteSpace) {
      startOffsetLocal = element1.getTextRange().getEndOffset();
    }

    if (element2 instanceof PsiWhiteSpace) {
      endOffsetLocal = element2.getTextRange().getStartOffset();
    }

    assert element2 != null;
    if (";".equals(element2.getText())) endOffsetLocal = endOffsetLocal - 1;


    List<GrStatement> statements = new ArrayList<GrStatement>();
    GrStatement statement = PsiTreeUtil.findElementOfClassAtRange(file, startOffsetLocal, endOffsetLocal, GrStatement.class);

    statements.add(statement);
    while (statement != null) {
      startOffsetLocal = statement.getTextRange().getEndOffset();
      statement = PsiTreeUtil.findElementOfClassAtRange(file, startOffsetLocal, endOffsetLocal, GrStatement.class);
      
      if (statement == null) break;
      statements.add(statement);
    }

    if (startOffsetLocal != endOffsetLocal) return null;

    return statements.toArray(new GrStatement[0]);
  }

  static Surrounder[] surrounders = new Surrounder[]{
      new GroovyWithBracketsSurrounder(),
      new GroovyWithTryCatchSurrounder(),
      new GroovyWithTryFinallySurrounder(),
      new GroovyWithTryCatchFinallySurrounder(),

      new GroovySurrounderByParametrizedClosure(),
      new GroovySurrounderByClosure()
  };

  @NotNull
  public Surrounder[] getSurrounders() {
    return surrounders;
  }
}
