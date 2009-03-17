package com.intellij.codeInspection.i18n.folding;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInspection.i18n.JavaI18nUtil;
import com.intellij.lang.ASTNode;
import com.intellij.lang.folding.FoldingBuilder;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.lang.properties.psi.Property;
import com.intellij.lang.properties.references.I18nUtil;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.*;
import com.intellij.psi.impl.ConstantExpressionEvaluator;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Konstantin Bulenkov
 */
public class PropertyFoldingBuilder implements FoldingBuilder {
  public FoldingDescriptor[] buildFoldRegions(ASTNode node, Document document) {
    final PsiElement element = node.getPsi();
    if (!(element instanceof PsiJavaFile)) {
      return FoldingDescriptor.EMPTY;
    }
    final PsiJavaFile file = (PsiJavaFile) element;
    final List<FoldingDescriptor> result = new ArrayList<FoldingDescriptor>();

    file.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitLiteralExpression(PsiLiteralExpression expression) {
        if (isI18nProperty(expression)) {
          final String msg = getI18nMessage(expression);

          final PsiElement parent = expression.getParent();
          if (!msg.equals(expression.getText()) &&
              parent instanceof PsiExpressionList &&
              ((PsiExpressionList)parent).getExpressions()[0] == expression) {
            final PsiExpressionList expressions = (PsiExpressionList)parent;
            final int count = I18nUtil.getPropertyValueParamsMaxCount(expression);
            final PsiExpression[] args = expressions.getExpressions();
            if (args.length == 1 + count && parent.getParent() instanceof PsiMethodCallExpression) {
              boolean ok = true;
              for (int i = 1; i < count + 1; i++) {
                Object value = ConstantExpressionEvaluator.computeConstantExpression(args[i], false);
                if (value == null) {
                  if (!(args[i] instanceof PsiReferenceExpression)) {
                    ok = false;
                    break;
                  }
                }
              }
              if (ok) {
                result.add(new FoldingDescriptor(parent.getParent().getNode(), parent.getParent().getTextRange()));
                return;
              }
            }
          }

          result.add(new FoldingDescriptor(expression.getNode(), expression.getTextRange()));
        }
      }
    });

    return result.toArray(new FoldingDescriptor[result.size()]);
  }



  public String getPlaceholderText(ASTNode node) {
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
      final int count = I18nUtil.getPropertyValueParamsMaxCount((PsiLiteralExpression)args[0]);
      if (args.length == 1 + count) {
        String text = getI18nMessage((PsiLiteralExpression)args[0]);
        for (int i = 1; i < count + 1; i++) {
          Object value = ConstantExpressionEvaluator.computeConstantExpression(args[i], false);
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
          return text;
        }
      }
    }

    return methodCallExpression.getText();
  }

  private static String getI18nMessage(PsiLiteralExpression literal) {
    if (isI18nProperty(literal)) {
      final PsiReference[] references = literal.getReferences();
      for (PsiReference reference : references) {
        final PsiElement element = reference.resolve();
        if (element instanceof Property) {
          return "\"" + ((Property)element).getValue() + "\"";
        }
      }
    }
    return literal.getText();
  }

  public boolean isCollapsedByDefault(ASTNode node) {
    return true;
  }


  private static boolean isI18nProperty(PsiLiteralExpression expr) {
    final Map<String, Object> annotationParams = new HashMap<String, Object>();
    annotationParams.put(AnnotationUtil.PROPERTY_KEY_RESOURCE_BUNDLE_PARAMETER, null);
    return JavaI18nUtil.mustBePropertyKey(expr, annotationParams);
  }
}
