// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.intentions.conversions.strings;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.intentions.base.Intention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrString;
import org.jetbrains.plugins.groovy.lang.psi.util.GrStringUtil;

/**
 * @author Max Medvedev
 */
public class RemoveUnnecessaryEscapeCharactersIntention extends Intention {
  public static final String HINT = "Remove unnecessary escape characters";

  @Override
  protected void processIntention(@NotNull PsiElement element, @NotNull Project project, Editor editor) throws IncorrectOperationException {
    final Document document = editor.getDocument();
    final TextRange range = element.getTextRange();

    document.replaceString(range.getStartOffset(), range.getEndOffset(), removeUnnecessaryEscapeSymbols((GrLiteral)element));
  }

  @NotNull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new PsiElementPredicate() {
      @Override
      public boolean satisfiedBy(@NotNull PsiElement element) {
        if (!(element instanceof GrLiteral)) return false;

        String text = element.getText();
        return GrStringUtil.getStartQuote(text) != null && !removeUnnecessaryEscapeSymbols((GrLiteral)element).equals(text);
      }
    };
  }

  private static String removeUnnecessaryEscapeSymbols(final GrLiteral literal) {
    final String text = literal.getText();
    final String quote = GrStringUtil.getStartQuote(text);
    final String value = GrStringUtil.removeQuotes(text);

    final StringBuilder buffer = new StringBuilder();
    buffer.append(quote);

    switch (quote) {
      case "'" -> GrStringUtil.escapeAndUnescapeSymbols(value, "", "\"$", buffer);
      case "'''" -> {
        int position = buffer.length();
        GrStringUtil.escapeAndUnescapeSymbols(value, "", "\"'$n", buffer);
        GrStringUtil.fixAllTripleQuotes(buffer, position);
      }
      case "\"" -> {
        if (literal instanceof GrString) {
          final ASTNode node = literal.getNode();
          for (ASTNode child = node.getFirstChildNode(); child != null; child = child.getTreeNext()) {
            final IElementType type = child.getElementType();
            if (type == GroovyTokenTypes.mGSTRING_BEGIN || type == GroovyTokenTypes.mGSTRING_END) continue;
            if (type == GroovyElementTypes.GSTRING_INJECTION) {
              buffer.append(child.getText());
            }
            else {
              GrStringUtil.escapeAndUnescapeSymbols(child.getText(), "", "'", buffer);
            }
          }
        }
        else {
          GrStringUtil.escapeAndUnescapeSymbols(value, "", "'", buffer);
        }
      }
      case "\"\"\"" -> {
        if (literal instanceof GrString) {
          final ASTNode node = literal.getNode();
          for (ASTNode child = node.getFirstChildNode(); child != null; child = child.getTreeNext()) {
            final IElementType type = child.getElementType();
            if (type == GroovyTokenTypes.mGSTRING_BEGIN || type == GroovyTokenTypes.mGSTRING_END) continue;
            if (type == GroovyElementTypes.GSTRING_INJECTION) {
              buffer.append(child.getText());
            }
            else {
              final int position = buffer.length();
              GrStringUtil.escapeAndUnescapeSymbols(child.getText(), "", "\"'n", buffer);
              GrStringUtil.fixAllTripleDoubleQuotes(buffer, position);
            }
          }
        }
        else {
          final int position = buffer.length();
          GrStringUtil.escapeAndUnescapeSymbols(value, "", "\"'n", buffer);
          GrStringUtil.fixAllTripleDoubleQuotes(buffer, position);
        }
      }
      default -> {
        return text;
      }
    }

    buffer.append(quote);

    return buffer.toString();
  }
}
