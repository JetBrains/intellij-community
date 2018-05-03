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
package com.intellij.codeInspection.i18n.folding;

import com.intellij.codeInsight.folding.JavaCodeFoldingSettings;
import com.intellij.codeInspection.i18n.JavaI18nUtil;
import com.intellij.lang.ASTNode;
import com.intellij.lang.StdLanguages;
import com.intellij.lang.folding.FoldingBuilderEx;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.lang.folding.NamedFoldingDescriptor;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.parsing.PropertiesElementTypes;
import com.intellij.lang.properties.psi.Property;
import com.intellij.lang.properties.psi.impl.PropertyImpl;
import com.intellij.lang.properties.psi.impl.PropertyStubImpl;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

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
    if (!(element instanceof PsiFile) || quick || !isFoldingsOn()) {
      return FoldingDescriptor.EMPTY;
    }
    final PsiFile file = (PsiFile)element;
    final List<FoldingDescriptor> result = new ArrayList<>();
    boolean hasJsp = ContainerUtil.intersects(Arrays.asList(StdLanguages.JSP, StdLanguages.JSPX), file.getViewProvider().getLanguages());
    //hack here because JspFile PSI elements are not threaded correctly via nextSibling/prevSibling
    file.accept(hasJsp ? new JavaRecursiveElementVisitor() {
      @Override
      public void visitLiteralExpression(PsiLiteralExpression expression) {
        ULiteralExpression uLiteralExpression = UastContextKt.toUElement(expression, ULiteralExpression.class);
        if (uLiteralExpression != null) {
          checkLiteral(uLiteralExpression, result);
        }
      }
    } : new PsiRecursiveElementVisitor() {

      @Override
      public void visitElement(PsiElement element) {
        ULiteralExpression uLiteralExpression = UastContextKt.toUElement(element, ULiteralExpression.class);
        if (uLiteralExpression != null) {
          checkLiteral(uLiteralExpression, result);
        }
        super.visitElement(element);
      }
    });

    return result.toArray(FoldingDescriptor.EMPTY);
  }

  private static boolean isFoldingsOn() {
    return JavaCodeFoldingSettings.getInstance().isCollapseI18nMessages();
  }

  private static void checkLiteral(ULiteralExpression expression, List<FoldingDescriptor> result) {
    PsiElement sourcePsi = expression.getSourcePsi();
    if (sourcePsi == null) return;
    if (!isI18nProperty(expression)) return;
    final IProperty property = getI18nProperty(expression);
    final HashSet<Object> set = new HashSet<>();
    set.add(property != null ? property : PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT);
    final String msg = formatI18nProperty(expression, property);

    final UElement parent = expression.getUastParent();
    if (!msg.equals(UastLiteralUtils.getValueIfStringLiteral(expression)) &&
        parent instanceof UCallExpression &&
        ((UCallExpression)parent).getValueArguments().get(0).getSourcePsi() == expression.getSourcePsi()) {
      final UCallExpression expressions = (UCallExpression)parent;
      PsiElement callSourcePsi = expressions.getSourcePsi();
      if (callSourcePsi == null) return;
      final int count = JavaI18nUtil.getPropertyValueParamsMaxCount(expression);
      final List<UExpression> args = expressions.getValueArguments();
      if (args.size() == 1 + count) {
        boolean ok = true;
        for (int i = 1; i < count + 1; i++) {
          Object value = args.get(i).evaluate();
          if (value == null) {
            if (!(args.get(i) instanceof UReferenceExpression)) {
              ok = false;
              break;
            }
          }
        }
        if (ok) {
          UExpression receiver = expressions.getReceiver();
          PsiElement receiverSourcePsi = receiver != null ? receiver.getSourcePsi() : null;
          PsiElement elementToFold = null;
          if (receiverSourcePsi != null) {
            elementToFold = PsiTreeUtil.findCommonParent(callSourcePsi, receiverSourcePsi);
          }
          if (elementToFold == null) {
            elementToFold = callSourcePsi;
          }
          result.add(
            new NamedFoldingDescriptor(ObjectUtils.assertNotNull(elementToFold.getNode()), elementToFold.getTextRange(), null,
                                       formatMethodCallExpression(expressions), isFoldingsOn(), set));
          return;
        }
      }
    }
    result.add(new NamedFoldingDescriptor(ObjectUtils.assertNotNull(sourcePsi.getNode()), sourcePsi.getTextRange(), null,
                                          getI18nMessage(expression), isFoldingsOn(), set));
  }


  @Override
  public String getPlaceholderText(@NotNull ASTNode node) {
    return null;
  }

  @NotNull
  private static String formatMethodCallExpression(@NotNull UCallExpression methodCallExpression) {
    final List<UExpression> args = methodCallExpression.getValueArguments();
    PsiElement callSourcePsi = methodCallExpression.getSourcePsi();
    if (args.size() > 0
        && args.get(0) instanceof ULiteralExpression
        && isI18nProperty((ULiteralExpression)args.get(0))) {
      final int count = JavaI18nUtil.getPropertyValueParamsMaxCount(args.get(0));
      if (args.size() == 1 + count) {
        String text = getI18nMessage((ULiteralExpression)args.get(0));
        for (int i = 1; i < count + 1; i++) {
          Object value = args.get(i).evaluate();
          if (value == null) {
            if (args.get(i) instanceof UReferenceExpression) {
              PsiElement sourcePsi = args.get(i).getSourcePsi();
              value = "{" + (sourcePsi != null ? sourcePsi.getText() : "<error>") + "}";
            }
            else {
              text = null;
              break;
            }
          }
          text = text.replace("{" + (i - 1) + "}", value.toString());
        }
        if (text != null) {
          if (callSourcePsi != null && !text.equals(callSourcePsi.getText())) {
            text = text.replace("''", "'");
          }
          return text.length() > FOLD_MAX_LENGTH ? text.substring(0, FOLD_MAX_LENGTH - 3) + "...\"" : text;
        }
      }
    }

    return callSourcePsi != null ? callSourcePsi.getText() : "<invalid>";
  }

  @NotNull
  private static String getI18nMessage(@NotNull ULiteralExpression literal) {
    final IProperty property = getI18nProperty(literal);
    return property == null ? UastLiteralUtils.getValueIfStringLiteral(literal) : formatI18nProperty(literal, property);
  }

  @Nullable
  public static IProperty getI18nProperty(@NotNull ULiteralExpression literal) {
    PsiElement sourcePsi = literal.getSourcePsi();
    if (sourcePsi == null) return null;
    final Property property = (Property)sourcePsi.getUserData(CACHE);
    if (property == NULL) return null;
    if (property != null && isValid(property, literal)) return property;
    if (isI18nProperty(literal)) {
      final Iterable<PsiReference> references = UastLiteralUtils.getInjectedReferences(literal);
      for (PsiReference reference : references) {
        if (reference instanceof PsiPolyVariantReference) {
          final ResolveResult[] results = ((PsiPolyVariantReference)reference).multiResolve(false);
          for (ResolveResult result : results) {
            final PsiElement element = result.getElement();
            if (element instanceof IProperty) {
              IProperty p = (IProperty)element;
              sourcePsi.putUserData(CACHE, p);
              return p;
            }
          }
        }
        else {
          final PsiElement element = reference.resolve();
          if (element instanceof IProperty) {
            IProperty p = (IProperty)element;
            sourcePsi.putUserData(CACHE, p);
            return p;
          }
        }
      }
    }
    return null;
  }

  private static boolean isValid(Property property, ULiteralExpression literal) {
    if (literal == null || property == null || !property.isValid()) return false;
    Object result = literal.evaluate();
    if (!(result instanceof String)) return false;
    return StringUtil.unquoteString(((String)result)).equals(property.getKey());
  }

  @NotNull
  private static String formatI18nProperty(@NotNull ULiteralExpression literal, IProperty property) {
    Object evaluated = literal.evaluate();
    return property == null ?
           evaluated != null ? evaluated.toString() : "null" : "\"" + property.getValue() + "\"";
  }

  @Override
  public boolean isCollapsedByDefault(@NotNull ASTNode node) {
    return isFoldingsOn();
  }

  public static boolean isI18nProperty(@NotNull PsiLiteralExpression expr) {
    ULiteralExpression uLiteralExpression = UastContextKt.toUElement(expr, ULiteralExpression.class);
    if (uLiteralExpression == null) return false;
    return isI18nProperty(uLiteralExpression);
  }

  public static boolean isI18nProperty(@NotNull ULiteralExpression expr) {
    if (!expr.isString()) return false;
    PsiElement sourcePsi = expr.getSourcePsi();
    if (sourcePsi == null) return false;
    final IProperty property = sourcePsi.getUserData(CACHE);
    if (property == NULL) return false;
    if (property != null) return true;

    final boolean isI18n = JavaI18nUtil.mustBePropertyKey(expr);
    if (!isI18n) {
      sourcePsi.putUserData(CACHE, NULL);
    }
    return isI18n;
  }
}
