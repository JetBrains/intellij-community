// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.IntroduceTargetChooser;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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
import org.jetbrains.plugins.groovy.lang.psi.util.ErrorUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GrStringUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.ArrayList;
import java.util.List;

import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.STRING_DQ;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.STRING_TDQ;

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

  @Nullable
  @Override
  public PsiElement getElementToMakeWritable(@NotNull PsiFile file) {
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
      invokeImpl(expressions.get(0), document);
    }
    else if (!expressions.isEmpty()) {
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        invokeImpl(expressions.get(expressions.size() - 1), document);
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
    else if (operand instanceof GrLiteral literal) {
      String text = GrStringUtil.removeQuotes(operand.getText());

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
    if (!(expression instanceof GrReferenceExpression refExpr)) return false;

    final GrExpression qualifier = refExpr.getQualifierExpression();
    if (qualifier == null) return false;

    final GroovyResolveResult[] results = refExpr.multiResolve(false);
    if (results.length != 1) return false;

    final PsiElement element = results[0].getElement();
    if (!(element instanceof PsiMethod method)) return false;

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
    public boolean satisfiedBy(@NotNull PsiElement element) {
      return satisfied(element);
    }

    public static boolean satisfied(PsiElement element) {
      if (isApplicableLiteral(element)) {
        return true;
      }

      if (!(element instanceof GrBinaryExpression binaryExpression)) return false;

      if (!GroovyTokenTypes.mPLUS.equals(binaryExpression.getOperationTokenType())) return false;

      if (ErrorUtil.containsError(element)) return false;

      final PsiType type = binaryExpression.getType();
      if (type == null) return false;

      final PsiClassType stringType = TypesUtil.createType(CommonClassNames.JAVA_LANG_STRING, element);
      final PsiClassType gstringType = TypesUtil.createType(GroovyCommonClassNames.GROOVY_LANG_GSTRING, element);
      if (!(TypeConversionUtil.isAssignable(stringType, type) || TypeConversionUtil.isAssignable(gstringType, type))) return false;

      return true;
    }

    private static boolean isApplicableLiteral(PsiElement element) {
      if (!(element instanceof GrLiteral literal)) return false;
      if (!(literal.getValue() instanceof String)) return false;
      IElementType literalType = GrLiteralImpl.getLiteralType(literal);
      return literalType != STRING_DQ && literalType != STRING_TDQ;
    }
  }
}
