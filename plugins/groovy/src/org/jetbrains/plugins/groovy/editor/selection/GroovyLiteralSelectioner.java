/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.editor.selection;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;

import java.util.List;

/**
 * @author ilyas
 */
public class GroovyLiteralSelectioner extends GroovyBasicSelectioner {
  public boolean canSelect(PsiElement e) {
    PsiElement parent = e.getParent();
    return isStringLiteral(e) || isStringLiteral(parent);
  }

  private static boolean isStringLiteral(PsiElement element) {
    if (!(element instanceof GrLiteral)) return false;
    ASTNode node = element.getNode();
    if (node == null) return false;
    ASTNode[] children = node.getChildren(null);
    return children.length == 1 &&
        (children[0].getElementType() == GroovyTokenTypes.mSTRING_LITERAL ||
            children[0].getElementType() == GroovyTokenTypes.mGSTRING_LITERAL);
  }

  public List<TextRange> select(PsiElement e, CharSequence editorText, int cursorOffset, Editor editor) {
    List<TextRange> result = super.select(e, editorText, cursorOffset, editor);

    TextRange range = e.getTextRange();
    if (range.getLength() <= 2) {
      result.add(range);
    } else {
      result.add(new TextRange(range.getStartOffset() + 1, range.getEndOffset() - 1));
    }
    return result;
  }
}