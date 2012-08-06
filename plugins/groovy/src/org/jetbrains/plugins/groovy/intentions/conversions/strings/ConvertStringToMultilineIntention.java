/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.intentions.conversions.strings;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.editor.actions.GroovyEditorActionUtil;
import org.jetbrains.plugins.groovy.intentions.base.Intention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.literals.GrLiteralImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.literals.GrStringImpl;
import org.jetbrains.plugins.groovy.lang.psi.util.GrStringUtil;

/**
 * @author Max Medvedev
 */
public class ConvertStringToMultilineIntention extends Intention {
  private static final Logger LOG = Logger.getInstance(ConvertStringToMultilineIntention.class);

  public static final String hint = "Convert to Multiline";

  @Override
  protected void processIntention(@NotNull PsiElement element, Project project, Editor editor) throws IncorrectOperationException {
    String quote = element.getText().startsWith("'") ? "'''" : "\"\"\"";

    StringBuilder buffer = new StringBuilder();
    buffer.append(quote);

    GrExpression old;

    if (element instanceof GrLiteralImpl) {
      appendSimpleStringValue(element, buffer, quote);
      old = (GrExpression)element;
    }
    else {
      final GrStringImpl gstring = PsiTreeUtil.getParentOfType(element, GrStringImpl.class);
      for (ASTNode child = gstring.getNode().getFirstChildNode(); child != null; child = child.getTreeNext()) {
        if (child.getElementType() == GroovyTokenTypes.mGSTRING_CONTENT) {
          appendGStringContent(child, buffer);
        }
        else if (child.getElementType() == GroovyElementTypes.GSTRING_INJECTION) {
          buffer.append(child.getText());
        }
      }
      old = gstring;
    }

    buffer.append(quote);
    try {
      final GrExpression newLiteral = GroovyPsiElementFactory.getInstance(project).createExpressionFromText(buffer.toString());
      old.replaceWithExpression(newLiteral, true);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  private static void appendGStringContent(ASTNode child, StringBuilder buffer) {
    final String text = child.getText();
    final StringBuilder parsed = new StringBuilder();
    if (GrStringUtil.parseStringCharacters(text, parsed, null)) {
      buffer.append(parsed);
    }
    else {
      buffer.append(text);
    }
  }

  private static void appendSimpleStringValue(PsiElement element, StringBuilder buffer, String quote) {
    final String text = GrStringUtil.removeQuotes(element.getText());
    final int position = buffer.length();
    if ("'''".equals(quote)) {
      GrStringUtil.escapeAndUnescapeSymbols(text, "", "'n", buffer);
      GrStringUtil.fixAllTripleQuotes(buffer, position);
    }
    else {
      GrStringUtil.escapeAndUnescapeSymbols(text, "", "\"n", buffer);
      GrStringUtil.fixAllTripleDoubleQuotes(buffer, position);
    }
  }

  @NotNull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new PsiElementPredicate() {
      @Override
      public boolean satisfiedBy(PsiElement element) {
        if (element instanceof GrLiteralImpl) {
          final ASTNode node = element.getFirstChild().getNode();
          final IElementType type = node.getElementType();
          if (type == GroovyTokenTypes.mSTRING_LITERAL) {
            return GroovyEditorActionUtil.isPlainStringLiteral(node);
          }
          if (type == GroovyTokenTypes.mGSTRING_LITERAL) {
            return GroovyEditorActionUtil.isPlainGString(node);
          }
        }
        else {
          final GrStringImpl gstring = PsiTreeUtil.getParentOfType(element, GrStringImpl.class, false, GrMember.class, GroovyFile.class);
          if (gstring == null) return false;
          return gstring.isPlainString();
        }
        return false;
      }
    };
  }
}
