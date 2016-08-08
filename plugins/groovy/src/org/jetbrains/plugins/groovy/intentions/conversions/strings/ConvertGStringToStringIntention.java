/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.intentions.base.Intention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrReturnStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrStringInjection;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GrStringUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.ArrayList;

public class ConvertGStringToStringIntention extends Intention {
  public static final String INTENTION_NAME = "Convert to String";

  @Override
  @NotNull
  public PsiElementPredicate getElementPredicate() {
    return new ConvertibleGStringLiteralPredicate();
  }

  @Override
  public void processIntention(@NotNull PsiElement element, Project project, Editor editor) throws IncorrectOperationException {
    final GrLiteral exp = (GrLiteral)element;
    PsiImplUtil.replaceExpression(convertGStringLiteralToStringLiteral(exp), exp);
  }

  public static String convertGStringLiteralToStringLiteral(GrLiteral literal) {
    PsiElement child = literal.getFirstChild();
    if (child == null) return literal.getText();
    String text;

    ArrayList<String> list = new ArrayList<>();

    PsiElement prevSibling = null;
    PsiElement nextSibling;
    do {
      text = child.getText();
      nextSibling = child.getNextSibling();
      if (child instanceof GrStringInjection) {
        if (((GrStringInjection)child).getClosableBlock() != null) {
          text = prepareClosableBlock(((GrStringInjection)child).getClosableBlock());
        }
        else if (((GrStringInjection)child).getExpression() != null) {
          text = prepareExpression(((GrStringInjection)child).getExpression());
        }
        else {
          text = child.getText();
        }
      }
      else {
        text = prepareText(text, prevSibling == null, nextSibling == null,
                           nextSibling instanceof GrClosableBlock || nextSibling instanceof GrReferenceExpression);
      }
      if (text != null) {
        list.add(text);
      }
      prevSibling = child;
      child = child.getNextSibling();
    }
    while (child != null);

    StringBuilder builder = new StringBuilder(literal.getTextLength() * 2);

    if (list.isEmpty()) return "''";

    builder.append(list.get(0));
    for (int i = 1; i < list.size(); i++) {
      builder.append(" + ").append(list.get(i));
    }
    return builder.toString();
  }

  private static String prepareClosableBlock(GrClosableBlock block) {
    final GrStatement statement = block.getStatements()[0];
    final GrExpression expr;
    if (statement instanceof GrReturnStatement) {
      expr = ((GrReturnStatement)statement).getReturnValue();
    }
    else {
      expr = (GrExpression)statement;
    }
    return prepareExpression(expr);

  }

  private static String prepareExpression(GrExpression expr) {
    if (PsiUtil.isThisOrSuperRef(expr)) return expr.getText();
    String text = expr.getText();

    final PsiType type = expr.getType();
    if (type != null && CommonClassNames.JAVA_LANG_STRING.equals(type.getCanonicalText())) {
      if (expr instanceof GrBinaryExpression && GroovyTokenTypes.mPLUS.equals(((GrBinaryExpression)expr).getOperationTokenType())) {
        return '(' + text + ')';
      }
      else {
        return text;
      }
    }
    else {
      return "String.valueOf(" + text + ")";
    }
  }

  @Nullable
  private static String prepareText(String text, boolean isFirst, boolean isLast, boolean isBeforeInjection) {
    if (isFirst) {
      if (text.startsWith("\"\"\"")) {
        text = text.substring(3);
      }
      else text = StringUtil.trimStart(text, "\"");
    }
    if (isLast) {
      if (text.endsWith("\"\"\"")) {
        text = text.substring(0, text.length() - 3);
      }
      else text = StringUtil.trimEnd(text, "\"");
    }
    if (isBeforeInjection) {
      text = text.substring(0, text.length() - 1);
    }
    if (text.isEmpty()) return null;


    final StringBuilder buffer = new StringBuilder();
    if (text.indexOf('\n') >= 0) {
      GrStringUtil.escapeAndUnescapeSymbols(text, "", "\"$", buffer);
      GrStringUtil.fixAllTripleQuotes(buffer, 0);
    }
    else {
      GrStringUtil.escapeAndUnescapeSymbols(text, "'", "\"$", buffer);
    }
    return GrStringUtil.addQuotes(buffer.toString(), false);
  }
}
