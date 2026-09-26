// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.intentions.conversions.strings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pass;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.IntroduceTargetChooser;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.intentions.base.Intention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrString;
import org.jetbrains.plugins.groovy.lang.psi.util.GrStringUtil;
import org.jetbrains.plugins.groovy.util.GStringConcatenationUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Maxim.Medvedev
 */
public final class ConvertConcatenationToGstringIntention extends Intention {

  @Override
  protected @NotNull PsiElementPredicate getElementPredicate() {
    return new GStringConcatenationUtil.ConvertibleToGStringPredicate();
  }

  private static List<GrExpression> collectExpressions(final PsiFile file, final int offset) {
    final List<GrExpression> expressions = new ArrayList<>();

    _collect(file, offset, expressions);
    if (expressions.isEmpty()) _collect(file, offset, expressions);
    return expressions;
  }

  private static void _collect(PsiFile file, int offset, List<GrExpression> expressions) {
    final PsiElement elementAtCaret = file.findElementAt(offset);
    for (GrExpression expression = PsiTreeUtil.getParentOfType(elementAtCaret, GrExpression.class);
         expression != null;
         expression = PsiTreeUtil.getParentOfType(expression, GrExpression.class)) {
      if (GStringConcatenationUtil.ConvertibleToGStringPredicate.satisfied(expression)) {
        expressions.add(expression);
      }
      else if (!expressions.isEmpty()) break;
    }
  }

  @Override
  public @Nullable PsiElement getElementToMakeWritable(@NotNull PsiFile file) {
    return file;
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  protected void processIntention(@NotNull PsiElement element, @NotNull Project project, Editor editor) throws IncorrectOperationException {
    final PsiFile file = element.getContainingFile();
    final int offset = editor.getCaretModel().getOffset();
    final List<GrExpression> expressions = ReadAction.compute(() -> collectExpressions(file, offset));
    final Document document = editor.getDocument();
    if (expressions.size() == 1) {
      invokeImpl(expressions.getFirst(), document);
    }
    else if (!expressions.isEmpty()) {
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        invokeImpl(expressions.getLast(), document);
        return;
      }
      IntroduceTargetChooser.showChooser(editor, expressions,
                                         new Pass<>() {
                                           @Override
                                           public void pass(final GrExpression selectedValue) {
                                             invokeImpl(selectedValue, document);
                                           }
                                         },
                                         grExpression -> grExpression.getText()
      );
    }
  }

  private static void invokeImpl(final PsiElement element, Document document) {
    boolean isMultiline = containsMultilineStrings((GrExpression)element);

    StringBuilder builder = new StringBuilder(element.getTextLength());
    if (element instanceof GrBinaryExpression) {
      GStringConcatenationUtil.convertToGString((GrBinaryExpression)element, builder, isMultiline);
    }
    else if (element instanceof GrLiteral) {
      GStringConcatenationUtil.appendOperandText((GrExpression)element, builder, isMultiline);
    }
    else {
      return;
    }

    String text = builder.toString();
    final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(element.getProject());
    final GrExpression newExpr = factory.createExpressionFromText(GrStringUtil.addQuotes(text, true));

    CommandProcessor.getInstance().executeCommand(element.getProject(), () -> WriteAction.run(() -> {
      final GrExpression expression = ((GrExpression)element).replaceWithExpression(newExpr, true);
      if (expression instanceof GrString) {
        GrStringUtil.removeUnnecessaryBracesInGString((GrString)expression);
      }
    }), null, null, document);
  }

  private static boolean containsMultilineStrings(GrExpression expr) {
    final Ref<Boolean> result = Ref.create(false);
    expr.accept(new GroovyRecursiveElementVisitor() {
      @Override
      public void visitLiteralExpression(@NotNull GrLiteral literal) {
        if (GrStringUtil.isMultilineStringLiteral(literal) && literal.getText().contains("\n")) {
          result.set(true);
        }
      }

      @Override
      public void visitElement(@NotNull GroovyPsiElement element) {
        if (!result.get()) {
          super.visitElement(element);
        }
      }
    });
    return result.get();
  }
}
