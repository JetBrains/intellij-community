/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.intentions.GroovyIntentionsBundle;
import org.jetbrains.plugins.groovy.intentions.base.Intention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrStringContent;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrStringInjection;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.literals.GrLiteralImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.literals.GrStringImpl;
import org.jetbrains.plugins.groovy.lang.psi.util.GrStringUtil;

/**
 * @author Max Medvedev
 */
public class ConvertMultilineStringToSingleLineIntention extends Intention {
  private static final Logger LOG = Logger.getInstance(ConvertMultilineStringToSingleLineIntention.class);

  public static final String hint = GroovyIntentionsBundle.message("convert.multiline.string.to.single.line.intention.name");

  @Override
  protected void processIntention(@NotNull PsiElement element, Project project, Editor editor) throws IncorrectOperationException {
    String quote = element.getText().substring(0, 1);

    StringBuilder buffer = new StringBuilder();
    buffer.append(quote);

    GrExpression old;

    if (element instanceof GrLiteralImpl) {
      appendSimpleStringValue(element, buffer, quote);
      old = (GrExpression)element;
    }
    else {
      final GrStringImpl gstring = (GrStringImpl)element;
      for (GroovyPsiElement child : gstring.getAllContentParts()) {
        if (child instanceof GrStringContent) {
          appendSimpleStringValue(child, buffer, "\"");
        }
        else if (child instanceof GrStringInjection) {
          buffer.append(child.getText());
        }
      }
      old = gstring;
    }

    buffer.append(quote);
    try {
      final int offset = editor.getCaretModel().getOffset();
      final TextRange range = old.getTextRange();
      int shift;

      if (range.getStartOffset() == offset) {
        shift = 0;
      }
      else if (range.getStartOffset() == offset - 1) {
        shift = -1;
      }
      else if (range.getEndOffset() == offset) {
        shift = -4;
      }
      else if (range.getEndOffset() == offset + 1) {
        shift = -3;
      }
      else {
        shift = -2;
      }

      final GrExpression newLiteral = GroovyPsiElementFactory.getInstance(project).createExpressionFromText(buffer.toString());
      old.replaceWithExpression(newLiteral, true);

      if (shift != 0) {
        editor.getCaretModel().moveToOffset(offset + shift);
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  private static void appendSimpleStringValue(PsiElement element, StringBuilder buffer, String quote) {
    final String text = GrStringUtil.removeQuotes(element.getText());
    if ("'".equals(quote)) {
      GrStringUtil.escapeAndUnescapeSymbols(text, "\n'", "", buffer);
    }
    else {
      GrStringUtil.escapeAndUnescapeSymbols(text, "\"\n", "", buffer);
    }
  }

  @NotNull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new PsiElementPredicate() {
      @Override
      public boolean satisfiedBy(PsiElement element) {
        if (!(element instanceof GrLiteral)) return false;

        String text = element.getText();
        String quote = GrStringUtil.getStartQuote(text);
        return GrStringUtil.TRIPLE_QUOTES.equals(quote) ||
               GrStringUtil.TRIPLE_DOUBLE_QUOTES.equals(quote);
      }
    };
  }
}
