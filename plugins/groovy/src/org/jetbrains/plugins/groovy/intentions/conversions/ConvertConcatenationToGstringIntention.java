/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.intentions.conversions;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.intentions.base.ErrorUtil;
import org.jetbrains.plugins.groovy.intentions.base.Intention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrParenthesizedExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrString;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.util.GrStringUtil;

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

  @Override
  protected void processIntention(@NotNull PsiElement element, Project project, Editor editor) throws IncorrectOperationException {
    StringBuilder builder = new StringBuilder(element.getTextLength());
    if (element instanceof GrBinaryExpression) {
      performIntention((GrBinaryExpression)element, builder);
    }
    else if (element instanceof GrLiteral) {
      getOperandText((GrExpression)element, builder);
    }
    else {
      return;
    }
    final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(element.getProject());
    final GrExpression newExpr = factory.createExpressionFromText(GrStringUtil.addQuotes(builder.toString(), true));
    final GrExpression expression = ((GrExpression)element).replaceWithExpression(newExpr, true);
    if (expression instanceof GrString) {
      GrStringUtil.removeUnnecessaryBracesInGString((GrString)expression);
    }
  }

  private static void performIntention(GrBinaryExpression expr, StringBuilder builder) {
    GrExpression left = (GrExpression)skipParentheses(expr.getLeftOperand(), false);
    GrExpression right = (GrExpression)skipParentheses(expr.getRightOperand(), false);
    getOperandText(left, builder);
    getOperandText(right, builder);
  }

  private static void getOperandText(@Nullable GrExpression operand, StringBuilder builder) {
    if (operand instanceof GrString) {
      builder.append(GrStringUtil.removeQuotes(operand.getText()));
    }
    else if (operand instanceof GrLiteral) {
      String text = GrStringUtil.escapeSymbolsForGString(GrStringUtil.removeQuotes(operand.getText()), false);
      if (text.contains("\n")) text = GrStringUtil.escapeSymbolsForGString(text, true);
      builder.append(text);
    }
    else if (MyPredicate.satisfiedBy(operand, false)) {
      performIntention((GrBinaryExpression)operand, builder);
    }
    else if (isToStringMethod(operand, builder)) {
      //nothing to do
    }
    else {
      builder.append(START_BRACE).append(operand == null ? "" : operand.getText()).append(END_BRACE);
    }
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

  @Nullable
  private static PsiElement skipParentheses(PsiElement element, boolean up) {
    if (up) {
      PsiElement parent = element.getParent();
      while (parent instanceof GrParenthesizedExpression) {
        parent = parent.getParent();
      }
      return parent;
    }
    else {
      while (element instanceof GrParenthesizedExpression) {
        element = ((GrParenthesizedExpression)element).getOperand();
      }
      return element;
    }
  }

  private static class MyPredicate implements PsiElementPredicate {
    public boolean satisfiedBy(PsiElement element) {
      return satisfiedBy(element, true);
    }

    public static boolean satisfiedBy(PsiElement element, boolean checkForParent) {
      if (element instanceof GrLiteral && element.getText().startsWith("'")) return true;
      if (!(element instanceof GrBinaryExpression)) return false;
      GrBinaryExpression binaryExpression = (GrBinaryExpression)element;
      if (!GroovyTokenTypes.mPLUS.equals(binaryExpression.getOperationTokenType())) return false;

      if (checkForParent) {
        PsiElement parent = skipParentheses(binaryExpression, true);
        if (parent instanceof GrBinaryExpression && GroovyTokenTypes.mPLUS.equals(((GrBinaryExpression)parent).getOperationTokenType())) {
          return false;
        }
      }
      if (ErrorUtil.containsError(element)) return false;

      final PsiType type = binaryExpression.getType();
      if (type == null) return false;

      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(element.getProject());
      final PsiClassType stringType = factory.createTypeByFQClassName(CommonClassNames.JAVA_LANG_STRING, element.getResolveScope());
      final PsiClassType gstringType = factory.createTypeByFQClassName(GrStringUtil.GROOVY_LANG_GSTRING, element.getResolveScope());
      if (!(TypeConversionUtil.isAssignable(stringType, type) || TypeConversionUtil.isAssignable(gstringType, type))) return false;

      return true;
    }
  }
}
