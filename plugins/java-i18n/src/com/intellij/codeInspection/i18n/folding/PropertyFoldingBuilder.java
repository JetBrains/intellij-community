/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.codeInspection.i18n.folding;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.folding.JavaCodeFoldingSettings;
import com.intellij.codeInspection.i18n.JavaI18nUtil;
import com.intellij.lang.ASTNode;
import com.intellij.lang.StdLanguages;
import com.intellij.lang.folding.FoldingBuilderEx;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.*;
import com.intellij.psi.impl.JavaConstantExpressionEvaluator;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author Konstantin Bulenkov
 */
public class PropertyFoldingBuilder extends FoldingBuilderEx {
  private static final int FOLD_MAX_LENGTH = 50;

  @NotNull
  public FoldingDescriptor[] buildFoldRegions(@NotNull PsiElement element, @NotNull Document document, boolean quick) {
    if (!(element instanceof PsiJavaFile) || quick || !isFoldingsOn()) {
      return FoldingDescriptor.EMPTY;
    }
    final PsiJavaFile file = (PsiJavaFile) element;
    final List<FoldingDescriptor> result = new ArrayList<FoldingDescriptor>();
    boolean hasJsp = ContainerUtil.intersects(Arrays.asList(StdLanguages.JSP, StdLanguages.JSPX), file.getViewProvider().getLanguages());
    //hack here because JspFile PSI elements are not threaded correctly via nextSibling/prevSibling
    file.accept(hasJsp ? new JavaRecursiveElementVisitor() {
      @Override
      public void visitLiteralExpression(PsiLiteralExpression expression) {
        checkLiteral(expression, result);
      }
    } : new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitLiteralExpression(PsiLiteralExpression expression) {
        checkLiteral(expression, result);
      }
    });

    return result.toArray(new FoldingDescriptor[result.size()]);
  }

  private static boolean isFoldingsOn() {
    return JavaCodeFoldingSettings.getInstance().isCollapseI18nMessages();
  }

  private static void checkLiteral(PsiLiteralExpression expression, List<FoldingDescriptor> result) {
    if (isI18nProperty(expression)) {
      final String msg = getI18nMessage(expression);

      final PsiElement parent = expression.getParent();
      if (!msg.equals(expression.getText()) &&
          parent instanceof PsiExpressionList &&
          ((PsiExpressionList)parent).getExpressions()[0] == expression) {
        final PsiExpressionList expressions = (PsiExpressionList)parent;
        final int count = JavaI18nUtil.getPropertyValueParamsMaxCount(expression);
        final PsiExpression[] args = expressions.getExpressions();
        if (args.length == 1 + count && parent.getParent() instanceof PsiMethodCallExpression) {
          boolean ok = true;
          for (int i = 1; i < count + 1; i++) {
            Object value = JavaConstantExpressionEvaluator.computeConstantExpression(args[i], false);
            if (value == null) {
              if (!(args[i] instanceof PsiReferenceExpression)) {
                ok = false;
                break;
              }
            }
          }
          if (ok) {
            result.add(new FoldingDescriptor(parent.getParent(), parent.getParent().getTextRange()));
            return;
          }
        }
      }

      result.add(new FoldingDescriptor(expression, expression.getTextRange()));
    }
  }


  public String getPlaceholderText(@NotNull ASTNode node) {
    final PsiElement element = SourceTreeToPsiMap.treeElementToPsi(node);
    if (element instanceof PsiLiteralExpression) {
      return getI18nMessage((PsiLiteralExpression)element);
    } else if (element instanceof PsiMethodCallExpression) {
      return formatMethodCallExpression((PsiMethodCallExpression)element);
    }
    return element.getText();
  }

  private static String formatMethodCallExpression(PsiMethodCallExpression methodCallExpression) {
    final PsiExpression[] args = methodCallExpression.getArgumentList().getExpressions();
    if (args.length > 0
        && args[0] instanceof PsiLiteralExpression
        && isI18nProperty((PsiLiteralExpression)args[0])) {
      final int count = JavaI18nUtil.getPropertyValueParamsMaxCount((PsiLiteralExpression)args[0]);
      if (args.length == 1 + count) {
        String text = getI18nMessage((PsiLiteralExpression)args[0]);
        for (int i = 1; i < count + 1; i++) {
          Object value = JavaConstantExpressionEvaluator.computeConstantExpression(args[i], false);
          if (value == null) {
            if (args[i] instanceof PsiReferenceExpression) {
              value = "{" + args[i].getText() + "}";
            }
            else {
              text = null;
              break;
            }
          }
          text = text.replace("{" + (i - 1) + "}", value.toString());
        }
        if (text != null) {
          if (!text.equals(methodCallExpression.getText())) {
            text = text.replace("''", "'");
          }
          return text.length() > FOLD_MAX_LENGTH ? text.substring(0, FOLD_MAX_LENGTH - 3) + "..." : text;
        }
      }
    }

    return methodCallExpression.getText();
  }

  private static String getI18nMessage(PsiLiteralExpression literal) {
    if (isI18nProperty(literal)) {
      final PsiReference[] references = literal.getReferences();
      for (PsiReference reference : references) {
        if (reference instanceof PsiPolyVariantReference) {
          final ResolveResult[] results = ((PsiPolyVariantReference)reference).multiResolve(false);
          for (ResolveResult result : results) {
            final PsiElement element = result.getElement();
            if (element instanceof Property) {
              return "\"" + ((Property)element).getValue() + "\"";
            }            
          }
        } else {
          final PsiElement element = reference.resolve();
          if (element instanceof Property) {
            return "\"" + ((Property)element).getValue() + "\"";
          }
        }
      }
    }
    return literal.getText();
  }

  public boolean isCollapsedByDefault(@NotNull ASTNode node) {
    return isFoldingsOn();
  }


  public static boolean isI18nProperty(PsiLiteralExpression expr) {
    if (! isStringLiteral(expr)) return false;

    final Map<String, Object> annotationParams = new HashMap<String, Object>();
    annotationParams.put(AnnotationUtil.PROPERTY_KEY_RESOURCE_BUNDLE_PARAMETER, null);
    return JavaI18nUtil.mustBePropertyKey(expr, annotationParams);
  }

  private static boolean isStringLiteral(PsiLiteralExpression expr) {
    final String text;
    if (expr == null || (text = expr.getText()) == null) return false;
    return text.startsWith("\"") && text.endsWith("\"") && text.length() > 2;
  }
}
