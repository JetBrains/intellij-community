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

package org.jetbrains.plugins.groovy.intentions.conversions.strings;

import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pass;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.IntroduceTargetChooser;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.util.ErrorUtil;
import org.jetbrains.plugins.groovy.intentions.base.Intention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.literals.GrLiteralImpl;
import org.jetbrains.plugins.groovy.lang.psi.util.GrStringUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Maxim.Medvedev
 */
public class ConvertConcatenationToGstringIntention extends Intention {
  private static final String END_BRACE = "}";
  private static final String START_BRACE = "${";

  @NotNull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new MyPredicate();
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
      if (MyPredicate.satisfied(expression)) {
        expressions.add(expression);
      }
      else if (!expressions.isEmpty()) break;
    }
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  protected void processIntention(@NotNull PsiElement element, Project project, Editor editor) throws IncorrectOperationException {
    final PsiFile file = element.getContainingFile();
    final int offset = editor.getCaretModel().getOffset();
    final AccessToken accessToken = ReadAction.start();
    final List<GrExpression> expressions;
    try {
      expressions = collectExpressions(file, offset);
    }
    finally {
      accessToken.finish();
    }
    final Document document = editor.getDocument();
    if (expressions.size() == 1) {
      invokeImpl(expressions.get(0), document);
    }
    else if (!expressions.isEmpty()) {
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        invokeImpl(expressions.get(expressions.size() - 1), document);
        return;
      }
      IntroduceTargetChooser.showChooser(editor, expressions,
                                         new Pass<GrExpression>() {
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
      performIntention((GrBinaryExpression)element, builder, isMultiline);
    }
    else if (element instanceof GrLiteral) {
      getOperandText((GrExpression)element, builder, isMultiline);
    }
    else {
      return;
    }

    String text = builder.toString();
    final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(element.getProject());
    final GrExpression newExpr = factory.createExpressionFromText(GrStringUtil.addQuotes(text, true));

    CommandProcessor.getInstance().executeCommand(element.getProject(), () -> {
      final AccessToken accessToken = WriteAction.start();
      try {
        final GrExpression expression = ((GrExpression)element).replaceWithExpression(newExpr, true);
        if (expression instanceof GrString) {
          GrStringUtil.removeUnnecessaryBracesInGString((GrString)expression);
        }
      }
      finally {
        accessToken.finish();
      }
    }, null, null, document);
  }

  private static boolean containsMultilineStrings(GrExpression expr) {
    final Ref<Boolean> result = Ref.create(false);
    expr.accept(new GroovyRecursiveElementVisitor() {
      @Override
      public void visitLiteralExpression(GrLiteral literal) {
        if (GrStringUtil.isMultilineStringLiteral(literal) && literal.getText().contains("\n")) {
          result.set(true);
        }
      }

      @Override
      public void visitElement(GroovyPsiElement element) {
        if (!result.get()) {
          super.visitElement(element);
        }
      }
    });
    return result.get();
  }

  private static void performIntention(GrBinaryExpression expr, StringBuilder builder, boolean multiline) {
    GrExpression left = (GrExpression)PsiUtil.skipParentheses(expr.getLeftOperand(), false);
    GrExpression right = (GrExpression)PsiUtil.skipParentheses(expr.getRightOperand(), false);
    getOperandText(left, builder, multiline);
    getOperandText(right, builder, multiline);
  }

  private static void getOperandText(@Nullable GrExpression operand, StringBuilder builder, boolean multiline) {
    if (operand instanceof GrRegex) {
      for (GroovyPsiElement element : ((GrRegex)operand).getAllContentParts()) {
        if (element instanceof GrStringInjection) {
          builder.append(element.getText());
        }
        else if (element instanceof GrStringContent) {
          if (GrStringUtil.isDollarSlashyString((GrLiteral)operand)) {
            processDollarSlashyContent(builder, multiline, element.getText());
          }
          else {
            processSlashyContent(builder, multiline, element.getText());
          }
        }
      }
    }
    else if (operand instanceof GrString) {
      boolean isMultiline = GrStringUtil.isMultilineStringLiteral((GrLiteral)operand);
      for (GroovyPsiElement element : ((GrString)operand).getAllContentParts()) {
        if (element instanceof GrStringInjection) {
          builder.append(element.getText());
        }
        else if (element instanceof GrStringContent) {
          if (isMultiline) {
            processMultilineGString(builder, element.getText());
          }
          else {
            processSinglelineGString(builder, element.getText());
          }
        }
      }
    }
    else if (operand instanceof GrLiteral) {
      String text = GrStringUtil.removeQuotes(operand.getText());
      GrLiteral literal = (GrLiteral)operand;

      if (GrStringUtil.isSingleQuoteString(literal)) {
        processSinglelineString(builder, text);
      }
      else if (GrStringUtil.isTripleQuoteString(literal)) {
        processMultilineString(builder, text);
      }
      else if (GrStringUtil.isDoubleQuoteString(literal)) {
        processSinglelineGString(builder, text);
      }
      else if (GrStringUtil.isTripleDoubleQuoteString(literal)) {
        processMultilineGString(builder, text);
      }
      else if (GrStringUtil.isSlashyString(literal)) {
        processSlashyContent(builder, multiline, text);
      }
      else if (GrStringUtil.isDollarSlashyString(literal)) {
        processDollarSlashyContent(builder, multiline, text);
      }
    }
    else if (MyPredicate.satisfied(operand)) {
      performIntention((GrBinaryExpression)operand, builder, multiline);
    }
    else if (isToStringMethod(operand, builder)) {
      //nothing to do
    }
    else {
      builder.append(START_BRACE).append(operand == null ? "" : operand.getText()).append(END_BRACE);
    }
  }

  private static void processMultilineString(StringBuilder builder, String text) {
    final int position = builder.length();
    GrStringUtil.escapeAndUnescapeSymbols(text, "$", "'\"", builder);
    GrStringUtil.fixAllTripleDoubleQuotes(builder, position);
  }

  private static void processSinglelineString(StringBuilder builder, String text) {
    GrStringUtil.escapeAndUnescapeSymbols(text, "$\"", "'", builder);
  }

  private static StringBuilder processSinglelineGString(StringBuilder builder, String text) {
    return builder.append(text);
  }

  private static void processMultilineGString(StringBuilder builder, String text) {
    StringBuilder raw = new StringBuilder(text);
    GrStringUtil.unescapeCharacters(raw, "\"", true);
    builder.append(raw);
  }

  private static void processDollarSlashyContent(StringBuilder builder, boolean multiline, String text) {
    GrStringUtil.escapeSymbolsForGString(text, !multiline, false, builder);
  }

  private static void processSlashyContent(StringBuilder builder, boolean multiline, String text) {
    String unescaped = GrStringUtil.unescapeSlashyString(text);
    GrStringUtil.escapeSymbolsForGString(unescaped, !multiline, false, builder);
  }

  /**
   * append text to builder if the operand is 'something'.toString()
   */
  private static boolean isToStringMethod(GrExpression operand, StringBuilder builder) {
    if (!(operand instanceof GrMethodCallExpression)) return false;

    final GrExpression expression = ((GrMethodCallExpression)operand).getInvokedExpression();
    if (!(expression instanceof GrReferenceExpression)) return false;

    final GrReferenceExpression refExpr = (GrReferenceExpression)expression;
    final GrExpression qualifier = refExpr.getQualifierExpression();
    if (qualifier == null) return false;

    final GroovyResolveResult[] results = refExpr.multiResolve(false);
    if (results.length != 1) return false;

    final PsiElement element = results[0].getElement();
    if (!(element instanceof PsiMethod)) return false;

    final PsiMethod method = (PsiMethod)element;
    final PsiClass objectClass =
      JavaPsiFacade.getInstance(operand.getProject()).findClass(CommonClassNames.JAVA_LANG_OBJECT, operand.getResolveScope());
    if (objectClass == null) return false;

    final PsiMethod[] toStringMethod = objectClass.findMethodsByName("toString", true);
    if (MethodSignatureUtil.isSubsignature(toStringMethod[0].getHierarchicalMethodSignature(), method.getHierarchicalMethodSignature())) {
      builder.append(START_BRACE).append(qualifier.getText()).append(END_BRACE);
      return true;
    }
    return false;
  }

  private static class MyPredicate implements PsiElementPredicate {
    @Override
    public boolean satisfiedBy(PsiElement element) {
      return satisfied(element);
    }

    public static boolean satisfied(PsiElement element) {
      if (element instanceof GrLiteral &&
          ((GrLiteral)element).getValue() instanceof String &&
          GrLiteralImpl.getLiteralType((GrLiteral)element) != GroovyTokenTypes.mGSTRING_LITERAL) {
        return true;
      }

      if (!(element instanceof GrBinaryExpression)) return false;

      GrBinaryExpression binaryExpression = (GrBinaryExpression)element;
      if (!GroovyTokenTypes.mPLUS.equals(binaryExpression.getOperationTokenType())) return false;

      if (ErrorUtil.containsError(element)) return false;

      final PsiType type = binaryExpression.getType();
      if (type == null) return false;

      final PsiClassType stringType = TypesUtil.createType(CommonClassNames.JAVA_LANG_STRING, element);
      final PsiClassType gstringType = TypesUtil.createType(GroovyCommonClassNames.GROOVY_LANG_GSTRING, element);
      if (!(TypeConversionUtil.isAssignable(stringType, type) || TypeConversionUtil.isAssignable(gstringType, type))) return false;

      return true;
    }
  }
}
