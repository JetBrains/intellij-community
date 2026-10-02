// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Max Medvedev
 */
public final class ConvertStringToMultilineIntention extends Intention {
  private static final Logger LOG = Logger.getInstance(ConvertStringToMultilineIntention.class);

  @Override
  protected void processIntention(@NotNull PsiElement element, @NotNull Project project, Editor editor) {
    final List<GrExpression> expressions;
    if (editor.getSelectionModel().hasSelection()) {
      expressions = Collections.singletonList(((GrExpression)element));
    }
    else {
      expressions = ReadAction.compute(() -> collectExpressions(element));
    }

    if (expressions.size() == 1) {
      invokeImpl(expressions.getFirst(), project, editor);
    }
    else if (ApplicationManager.getApplication().isUnitTestMode()) {
      invokeImpl(expressions.getLast(), project, editor);
    }
    else {
      final Pass<GrExpression> callback = new Pass<>() {
        @Override
        public void pass(GrExpression selectedValue) {
          invokeImpl(selectedValue, project, editor);
        }
      };
      final Function<GrExpression, String> renderer = grExpression -> grExpression.getText();
      IntroduceTargetChooser.showChooser(editor, expressions, callback, renderer);
    }
  }

  private static @NotNull List<GrExpression> collectExpressions(@NotNull PsiElement element) {
    assert element instanceof GrExpression;
    List<GrExpression> result = new ArrayList<>();
    result.add((GrExpression)element);
    while (element.getParent() instanceof GrBinaryExpression binary) {
      if (!isAppropriateBinary(binary)) break;

      result.add(binary);
      element = binary;
    }
    return result;
  }

  private static boolean isAppropriateBinary(@NotNull GrBinaryExpression binary) {
    return binary.getOperationTokenType() == GroovyTokenTypes.mPLUS
           && (containsOnlyLiterals(binary.getLeftOperand()))
           && containsOnlyLiterals(binary.getRightOperand());
  }

  private static boolean containsOnlyLiterals(@Nullable GrExpression expression) {
    if (expression instanceof GrLiteral) {
      final String quote = GrStringUtil.getStartQuote(expression.getText());
      if ("'".equals(quote) || "\"".equals(quote)) return true;
    }
    else if (expression instanceof GrBinaryExpression binaryExpression) {
      final IElementType type = binaryExpression.getOperationTokenType();
      if (type != GroovyTokenTypes.mPLUS) return false;

      final GrExpression left = binaryExpression.getLeftOperand();
      final GrExpression right = binaryExpression.getRightOperand();

      return containsOnlyLiterals(left) && containsOnlyLiterals(right);
    }

    return false;
  }

  private static @NotNull List<GrLiteral> collectOperands(@Nullable PsiElement element, @NotNull List<GrLiteral> initial) {
    if (element instanceof GrLiteral literal) {
      initial.add(literal);
    }
    else if (element instanceof GrBinaryExpression expression) {
      collectOperands(expression.getLeftOperand(), initial);
      collectOperands(expression.getRightOperand(), initial);
    }
    return initial;
  }

  private void invokeImpl(@NotNull GrExpression element, @NotNull Project project, @NotNull Editor editor) {
    final List<GrLiteral> literals = collectOperands(element, new ArrayList<>());
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
    String quote = (!containsInjections(literals) && literals.getFirst().getText().startsWith("'")) ? "'''" : "\"\"\"";

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
    if (GrStringUtil.endsWithUnescaped(buffer, quote.charAt(0))) {
      buffer.insert(buffer.length() - 1, '\\');
    }

    buffer.append(quote);
    return buffer;
  }

  private static boolean containsInjections(@NotNull List<GrLiteral> literals) {
    for (GrLiteral literal : literals) {
      if (literal instanceof GrString string && string.getInjections().length > 0) {
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

  @Override
  protected @NotNull PsiElementPredicate getElementPredicate() {
    return new PsiElementPredicate() {
      @Override
      public boolean satisfiedBy(@NotNull PsiElement element) {
        if (element instanceof GrLiteral) {
          String quote = GrStringUtil.getStartQuote(element.getText());
          return "\"".equals(quote) || "'".equals(quote);
        }
        return element instanceof GrBinaryExpression expression && isAppropriateBinary(expression);
      }
    };
  }

  @Override
  public @NotNull PsiElement getElementToMakeWritable(@NotNull PsiFile file) {
    return file;
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  public static String getHint() {
    return GroovyIntentionsBundle.message("convert.string.to.multiline.intention.name");
  }
}
