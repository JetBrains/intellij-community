/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.lang.surroundWith;

import com.intellij.lang.ASTNode;
import com.intellij.lang.surroundWith.SurroundDescriptor;
import com.intellij.lang.surroundWith.Surrounder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;

import java.util.ArrayList;
import java.util.List;

/**
 * User: Dmitry.Krasilschikov
 * Date: 22.05.2007
 */
public class GroovySurroundDescriptor implements SurroundDescriptor {
  private static final Surrounder[] ourSurrounders = new Surrounder[]{
    //statements: like in java
    new IfSurrounder(),
    new IfElseSurrounder(),
    new WhileSurrounder(),
    //there's no do-while in Groovy
    new SurrounderByClosure(),
    //like in Java
    new ForSurrounder(),
    new TryCatchSurrounder(),
    new TryFinallySurrounder(),
    new TryCatchFinallySurrounder(),
    //groovy-specific statements
    new ShouldFailWithTypeStatementsSurrounder(),
    //expressions: like in java
    new ParenthesisExprSurrounder(),
    new TypeCastSurrounder(),

    //groovy-specific
    new WithStatementsSurrounder(),

    new IfExprSurrounder(),
    new IfElseExprSurrounder(),
    new WhileExprSurrounder(),
    new WithExprSurrounder(),
  };

  @NotNull
  public Surrounder[] getSurrounders() {
    return ourSurrounders;
  }

  @NotNull
  public PsiElement[] getElementsToSurround(PsiFile file, int startOffset, int endOffset) {
    GrStatement[] statements = findStatementsInRange(file, startOffset, endOffset);

    if (statements == null) return PsiElement.EMPTY_ARRAY;
    return statements;
  }

  @Nullable
  private static GrStatement[] findStatementsInRange(PsiFile file, int startOffset, int endOffset) {

    GrStatement statement;
    int endOffsetLocal = endOffset;
    int startOffsetLocal = startOffset;

    List<GrStatement> statements = new ArrayList<GrStatement>();
    do {
      PsiElement element1 = file.findElementAt(startOffsetLocal);
      PsiElement element2 = file.findElementAt(endOffsetLocal - 1);

      if (element1 == null) break;
      ASTNode node1 = element1.getNode();
      assert node1 != null;
      if (element1 instanceof PsiWhiteSpace || TokenSets.WHITE_SPACE_TOKEN_SET.contains(node1.getElementType()) || GroovyTokenTypes.mNLS.equals(node1.getElementType())) {
        startOffsetLocal = element1.getTextRange().getEndOffset();
      }

      if (element2 == null) break;
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
      final PsiElement endSemicolon = file.findElementAt(startOffsetLocal);

      if (endSemicolon != null && ";".equals(endSemicolon.getText())) startOffsetLocal = startOffsetLocal + 1;
    } while (true);

    return statements.toArray(new GrStatement[0]);
  }
}
