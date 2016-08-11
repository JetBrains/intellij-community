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
package com.intellij.codeInspection.i18n.folding;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.folding.JavaCodeFoldingSettings;
import com.intellij.codeInspection.i18n.JavaI18nUtil;
import com.intellij.lang.ASTNode;
import com.intellij.lang.StdLanguages;
import com.intellij.lang.folding.FoldingBuilderEx;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.parsing.PropertiesElementTypes;
import com.intellij.lang.properties.psi.Property;
import com.intellij.lang.properties.psi.impl.PropertyImpl;
import com.intellij.lang.properties.psi.impl.PropertyStubImpl;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.JavaConstantExpressionEvaluator;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Konstantin Bulenkov
 */
public class PropertyFoldingBuilder extends FoldingBuilderEx {
  private static final int FOLD_MAX_LENGTH = 50;
  private static final Key<IProperty> CACHE = Key.create("i18n.property.cache");
  public static final IProperty NULL = new PropertyImpl(new PropertyStubImpl(null, null), PropertiesElementTypes.PROPERTY);

  @Override
  @NotNull
  public FoldingDescriptor[] buildFoldRegions(@NotNull PsiElement element, @NotNull Document document, boolean quick) {
    if (!(element instanceof PsiJavaFile) || quick || !isFoldingsOn()) {
      return FoldingDescriptor.EMPTY;
    }
    final PsiJavaFile file = (PsiJavaFile) element;
    final List<FoldingDescriptor> result = new ArrayList<>();
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
      final IProperty property = getI18nProperty(expression);
      final HashSet<Object> set = new HashSet<>();
      set.add(property != null ? property : PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT);
      final String msg = formatI18nProperty(expression, property);

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
            result.add(new FoldingDescriptor(ObjectUtils.assertNotNull(parent.getParent().getNode()), parent.getParent().getTextRange(), null, set));
            return;
          }
        }
      }

      result.add(new FoldingDescriptor(ObjectUtils.assertNotNull(expression.getNode()), expression.getTextRange(), null, set));
    }
  }


  @Override
  public String getPlaceholderText(@NotNull ASTNode node) {
    final PsiElement element = node.getPsi();
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
        && args[0].isValid()
        && isI18nProperty((PsiLiteralExpression)args[0])) {
      final int count = JavaI18nUtil.getPropertyValueParamsMaxCount(args[0]);
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
          return text.length() > FOLD_MAX_LENGTH ? text.substring(0, FOLD_MAX_LENGTH - 3) + "...\"" : text;
        }
      }
    }

    return methodCallExpression.getText();
  }

  private static String getI18nMessage(PsiLiteralExpression literal) {
    final IProperty property = getI18nProperty(literal);
    return property == null ? literal.getText() : formatI18nProperty(literal, property);
  }

  @Nullable
  public static IProperty getI18nProperty(PsiLiteralExpression literal) {
    final Property property = (Property)literal.getUserData(CACHE);
    if (property == NULL) return null;
    if (property != null && isValid(property, literal)) return property;
    if (isI18nProperty(literal)) {
      final PsiReference[] references = literal.getReferences();
      for (PsiReference reference : references) {
        if (reference instanceof PsiPolyVariantReference) {
          final ResolveResult[] results = ((PsiPolyVariantReference)reference).multiResolve(false);
          for (ResolveResult result : results) {
            final PsiElement element = result.getElement();
            if (element instanceof IProperty) {
              IProperty p = (IProperty)element;
              literal.putUserData(CACHE, p);
              return p;
            }
          }
        } else {
          final PsiElement element = reference.resolve();
          if (element instanceof IProperty) {
            IProperty p = (IProperty)element;
            literal.putUserData(CACHE, p);
            return p;
          }
        }
      }
    }
    return null;
  }

  private static boolean isValid(Property property, PsiLiteralExpression literal) {
    if (literal == null || property == null || !property.isValid()) return false;
    return StringUtil.unquoteString(literal.getText()).equals(property.getKey());
  }

  private static String formatI18nProperty(PsiLiteralExpression literal, IProperty property) {
    return property == null ?
           literal.getText() : "\"" + property.getValue() + "\"";
  }

  @Override
  public boolean isCollapsedByDefault(@NotNull ASTNode node) {
    return isFoldingsOn();
  }

  public static boolean isI18nProperty(@NotNull PsiLiteralExpression expr) {
    if (! isStringLiteral(expr)) return false;
    final IProperty property = expr.getUserData(CACHE);
    if (property == NULL) return false;
    if (property != null) return true;

    final Map<String, Object> annotationParams = new HashMap<>();
    annotationParams.put(AnnotationUtil.PROPERTY_KEY_RESOURCE_BUNDLE_PARAMETER, null);
    final boolean isI18n = JavaI18nUtil.mustBePropertyKey(expr, annotationParams);
    if (!isI18n) {
      expr.putUserData(CACHE, NULL);
    }
    return isI18n;
  }

  private static boolean isStringLiteral(PsiLiteralExpression expr) {
    final String text;
    if (expr == null || (text = expr.getText()) == null) return false;
    return text.startsWith("\"") && text.endsWith("\"") && text.length() > 2;
  }
}
