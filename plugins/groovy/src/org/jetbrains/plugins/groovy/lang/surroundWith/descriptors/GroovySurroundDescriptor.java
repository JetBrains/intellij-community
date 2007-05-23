package org.jetbrains.plugins.groovy.lang.surroundWith.descriptors;

import com.intellij.lang.surroundWith.SurroundDescriptor;
import com.intellij.lang.surroundWith.Surrounder;
import com.intellij.lang.ASTNode;
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
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;

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

    GrStatement statement;
    int endOffsetLocal = endOffset;
    int startOffsetLocal = startOffset;

    List<GrStatement> statements = new ArrayList<GrStatement>();
    do {
      PsiElement element1 = file.findElementAt(startOffsetLocal);
      PsiElement element2 = file.findElementAt(endOffsetLocal - 1);

      assert element1 != null;
      ASTNode node1 = element1.getNode();
      assert node1 != null;
      if (element1 instanceof PsiWhiteSpace || TokenSets.WHITE_SPACE_TOKEN_SET.contains(node1.getElementType()) || GroovyTokenTypes.mNLS.equals(node1.getElementType())) {
        startOffsetLocal = element1.getTextRange().getEndOffset();
      }

      assert element2 != null;
      ASTNode node2 = element2.getNode();
      assert node2 != null;
      if (element2 instanceof PsiWhiteSpace || TokenSets.WHITE_SPACE_TOKEN_SET.contains(node2.getElementType()) || GroovyTokenTypes.mNLS.equals(node2.getElementType())) {
        endOffsetLocal = element2.getTextRange().getStartOffset();
      }

      if (";".equals(element2.getText())) endOffsetLocal = endOffsetLocal - 1;

      statement = PsiTreeUtil.findElementOfClassAtRange(file, startOffsetLocal, endOffsetLocal, GrStatement.class);

      if (statement == null) break;
      statements.add(statement);

      startOffsetLocal = statement.getTextRange().getEndOffset();
    } while (true);

    return statements.toArray(new GrStatement[0]);
  }

  static Surrounder[] surrounders = new Surrounder[]{
      new GroovyWithBracketsSurrounder(),
      new GroovyWithTryCatchSurrounder(),
      new GroovyWithTryFinallySurrounder(),
      new GroovyWithTryCatchFinallySurrounder(),

      new GroovySurrounderByParametrizedClosure(),
//      new GroovySurrounderByOpenBlock()
  };

  @NotNull
  public Surrounder[] getSurrounders() {
    return surrounders;
  }
}
