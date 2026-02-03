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
package org.jetbrains.plugins.groovy.lang.surroundWith;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.TokenType;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.formatter.GrControlStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;

/**
 * @author Max Medvedev
 */
public class GrBracesSurrounder extends GroovyManyStatementsSurrounder {
  @Override
  public String getTemplateDescription() {
    return "{}";
  }

  @Override
  protected GroovyPsiElement doSurroundElements(PsiElement[] elements, PsiElement context) throws IncorrectOperationException {
    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(elements[0].getProject());

    final PsiElement e0 = elements[0];
    final PsiElement parent = e0.getParent();

    final GrCodeBlock block;
    if (parent instanceof GrControlStatement) {
      block = factory.createMethodBodyFromText("\n");
      final PsiElement prev = e0.getPrevSibling();
      if (prev != null && prev.getNode().getElementType().equals(GroovyTokenTypes.mNLS)) {
        final ASTNode parentNode = e0.getParent().getNode();
        parentNode.addLeaf(TokenType.WHITE_SPACE, " ", prev.getNode());
        parentNode.removeChild(prev.getNode());
      }
    }
    else {
      block = factory.createClosureFromText("{}");
    }

    GroovyManyStatementsSurrounder.addStatements(block, elements);
    return block;
  }

  @Override
  protected TextRange getSurroundSelectionRange(GroovyPsiElement element) {
    final int offset = element.getTextRange().getEndOffset();
    return new TextRange(offset, offset);
  }
}
