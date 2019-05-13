/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pass;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.refactoring.IntroduceTargetChooser;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.intentions.GroovyIntentionsBundle;
import org.jetbrains.plugins.groovy.intentions.base.Intention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrString;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrStringContent;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrStringInjection;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.literals.GrLiteralImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.literals.GrStringImpl;
import org.jetbrains.plugins.groovy.lang.psi.util.GrStringUtil;

import java.util.Collections;
import java.util.List;

/**
 * @author Max Medvedev
 */
public class ConvertStringToMultilineIntention extends Intention {
  private static final Logger LOG = Logger.getInstance(ConvertStringToMultilineIntention.class);

  public static final String hint = GroovyIntentionsBundle.message("convert.string.to.multiline.intention.name");

  @Override
  protected void processIntention(@NotNull PsiElement element, @NotNull final Project project, final Editor editor) throws IncorrectOperationException {
    final List<GrExpression> expressions;
    if (editor.getSelectionModel().hasSelection()) {
      expressions = Collections.singletonList(((GrExpression)element));
    }
    else {
      expressions = ReadAction.compute(() -> collectExpressions(element));
    }

    if (expressions.size() == 1) {
      invokeImpl(expressions.get(0), project, editor);
    }
    else if (ApplicationManager.getApplication().isUnitTestMode()) {
      invokeImpl(expressions.get(expressions.size() - 1), project, editor);
    }
    else {
      final Pass<GrExpression> callback = new Pass<GrExpression>() {
        @Override
        public void pass(final GrExpression selectedValue) {
          invokeImpl(selectedValue, project, editor);
        }
      };
      final Function<GrExpression, String> renderer = grExpression -> grExpression.getText();
      IntroduceTargetChooser.showChooser(editor, expressions, callback, renderer);
    }
  }

  @NotNull
  private static List<GrExpression> collectExpressions(@NotNull PsiElement element) {
    assert element instanceof GrExpression;
    List<GrExpression> result = ContainerUtil.newArrayList();
    result.add((GrExpression)element);
    while (element.getParent() instanceof GrBinaryExpression) {
      final GrBinaryExpression binary = (GrBinaryExpression)element.getParent();
      if (!isAppropriateBinary(binary, element)) break;

      result.add(binary);
      element = binary;
    }
    return result;
  }

  private static boolean isAppropriateBinary(@NotNull GrBinaryExpression binary, @Nullable PsiElement prevChecked) {
    if (binary.getOperationTokenType() == GroovyTokenTypes.mPLUS) {
      final GrExpression left = binary.getLeftOperand();
      final GrExpression right = binary.getRightOperand();
      if ((left != prevChecked || containsOnlyLiterals(right)) &&
          (right != prevChecked || containsOnlyLiterals(left))) {
        return true;
      }
    }

    return false;
  }

  private static boolean containsOnlyLiterals(@Nullable GrExpression expression) {
    if (expression instanceof GrLiteral) {
      final String text = expression.getText();
      if ("'".equals(GrStringUtil.getStartQuote(text))) return true;
      if ("\"".equals(GrStringUtil.getStartQuote(text))) return true;
    }
    else if (expression instanceof GrBinaryExpression) {
      final IElementType type = ((GrBinaryExpression)expression).getOperationTokenType();
      if (type != GroovyTokenTypes.mPLUS) return false;

      final GrExpression left = ((GrBinaryExpression)expression).getLeftOperand();
      final GrExpression right = ((GrBinaryExpression)expression).getRightOperand();

      return containsOnlyLiterals(left) && containsOnlyLiterals(right);
    }

    return false;
  }

  @NotNull
  private static List<GrLiteral> collectOperands(@Nullable PsiElement element, @NotNull List<GrLiteral> initial) {
    if (element instanceof GrLiteral) {
      initial.add((GrLiteral)element);
    }
    else if (element instanceof GrBinaryExpression) {
      collectOperands(((GrBinaryExpression)element).getLeftOperand(), initial);
      collectOperands(((GrBinaryExpression)element).getRightOperand(), initial);
    }
    return initial;
  }

  private void invokeImpl(@NotNull final GrExpression element, @NotNull final Project project, @NotNull final Editor editor) {
    final List<GrLiteral> literals = collectOperands(element, ContainerUtil.newArrayList());
    if (literals.isEmpty()) return;

    final StringBuilder buffer = prepareNewLiteralText(literals);

    CommandProcessor.getInstance().executeCommand(project, () -> ApplicationManager.getApplication().runWriteAction(() -> {
      try {
        final int offset = editor.getCaretModel().getOffset();
        final TextRange range = element.getTextRange();
        int shift;
        if (editor.getSelectionModel().hasSelection()) {
          shift = 0;
        }
        else if (range.getStartOffset() == offset) {
          shift = 0;
        }
        else if (range.getEndOffset() == offset + 1) {
          shift = -2;
        }
        else {
          shift = 2;
        }

        final GrExpression newLiteral = GroovyPsiElementFactory.getInstance(project).createExpressionFromText(buffer.toString());

        element.replaceWithExpression(newLiteral, true);

        if (shift != 0) {
          editor.getCaretModel().moveToOffset(editor.getCaretModel().getOffset() + shift);
        }
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }), getText(), null);
  }

  private static StringBuilder prepareNewLiteralText(List<GrLiteral> literals) {
    String quote = !containsInjections(literals) && literals.get(0).getText().startsWith("'") ? "'''" : "\"\"\"";

    final StringBuilder buffer = new StringBuilder();
    buffer.append(quote);

    for (GrLiteral literal : literals) {
      if (literal instanceof GrLiteralImpl) {
        appendSimpleStringValue(literal, buffer, quote);
      }
      else {
        final GrStringImpl gstring = (GrStringImpl)literal;
        for (PsiElement child : gstring.getAllContentParts()) {
          if (child instanceof GrStringContent) {
            appendSimpleStringValue(child, buffer, "\"\"\"");
          }
          else if (child instanceof GrStringInjection) {
            buffer.append(child.getText());
          }
        }
      }
    }

    buffer.append(quote);
    return buffer;
  }

  private static boolean containsInjections(@NotNull List<GrLiteral> literals) {
    for (GrLiteral literal : literals) {
      if (literal instanceof GrString && ((GrString)literal).getInjections().length > 0) {
        return true;
      }
    }
    return false;
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
      public boolean satisfiedBy(@NotNull PsiElement element) {
        return element instanceof GrLiteral && ("\"".equals(GrStringUtil.getStartQuote(element.getText())) ||
                                                "\'".equals(GrStringUtil.getStartQuote(element.getText())))
               || element instanceof GrBinaryExpression && isAppropriateBinary((GrBinaryExpression)element, null);
      }
    };
  }

  @Nullable
  @Override
  public PsiElement getElementToMakeWritable(@NotNull PsiFile file) {
    return file;
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
