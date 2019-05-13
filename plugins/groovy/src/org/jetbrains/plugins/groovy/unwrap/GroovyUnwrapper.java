/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.unwrap;

import com.intellij.codeInsight.unwrap.AbstractUnwrapper;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrBlockStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrIfStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;

import java.util.List;

public abstract class GroovyUnwrapper extends AbstractUnwrapper<GroovyUnwrapper.Context> {
  public GroovyUnwrapper(@NotNull String description) {
    super(description);
  }

  @Override
  protected Context createContext() {
    return new Context();
  }

  public static boolean isElseBlock(@Nullable final PsiElement element) {
    if (element == null) return false;
    final PsiElement parent = element.getParent();
    return parent instanceof GrIfStatement && element == ((GrIfStatement)parent).getElseBranch();
  }

  @NotNull
  @Override
  public List<PsiElement> unwrap(@NotNull Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    List<PsiElement> res = super.unwrap(editor, element);

    for (PsiElement e : res) {
      if (PsiImplUtil.isLeafElementOfType(e, GroovyTokenTypes.mNLS)) {
        CodeEditUtil.setNodeGenerated(e.getNode(), true);
      }
    }

    return res;
  }

  protected static class Context extends AbstractUnwrapper.AbstractContext {

    public void extractFromBlockOrSingleStatement(GrStatement block, PsiElement from) throws IncorrectOperationException {
      if (block instanceof GrBlockStatement) {
        extractFromCodeBlock(((GrBlockStatement)block).getBlock(), from);
      }
      else if (block != null) {
        extract(block, block, from);
      }
    }

    public void extractFromCodeBlock(GrCodeBlock block, PsiElement from) throws IncorrectOperationException {
      if (block == null) return;

      PsiElement rBrace = block.getRBrace();
      PsiElement lBrace = block.getLBrace();

      PsiElement firstBodyElement;
      if (lBrace == null) {
        firstBodyElement = null;
      }
      else {
        firstBodyElement = lBrace.getNextSibling();
        if (firstBodyElement == rBrace) {
          firstBodyElement = null;
        }
      }

      PsiElement lastBodyElement;
      if (rBrace == null) {
        lastBodyElement = null;
      }
      else {
        lastBodyElement = rBrace.getPrevSibling();
        if (lastBodyElement == lBrace) {
          lastBodyElement = null;
        }
      }

      extract(firstBodyElement, lastBodyElement, from);
    }

    @Override
    protected boolean isWhiteSpace(PsiElement element) {
      return org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil.isWhiteSpaceOrNls(element);
    }

    public void setElseBranch(GrIfStatement ifStatement, GrStatement elseBranch) throws IncorrectOperationException {
      GrStatement toExtract = elseBranch;
      if (myIsEffective) {
        ifStatement.replaceElseBranch(copyElement(elseBranch));
        toExtract = ifStatement.getElseBranch();
      }
      addElementToExtract(toExtract);
    }

    private static GrStatement copyElement(GrStatement e) throws IncorrectOperationException {
      // We cannot call el.copy() for 'else' since it sets context to parent 'if'.
      // This causes copy to be invalidated after parent 'if' is removed by setElseBranch method.
      GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(e.getProject());
      return factory.createStatementFromText(e.getText(), null);
    }

  }
}
