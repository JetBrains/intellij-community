package com.intellij.codeInspection.i18n.folding;

import com.intellij.lang.folding.FoldingBuilder;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInspection.i18n.JavaI18nUtil;

import java.util.*;

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
        final Map<String, Object> annotationParams = new HashMap<String, Object>();
        annotationParams.put(AnnotationUtil.PROPERTY_KEY_RESOURCE_BUNDLE_PARAMETER, null);
        final PsiElement parent = expression.getParent();
        if (JavaI18nUtil.mustBePropertyKey(expression, annotationParams) && parent instanceof PsiExpressionList) {
          if (((PsiExpressionList)parent).getExpressions().length == 1 && parent.getParent() instanceof PsiMethodCallExpression) {
            final PsiMethodCallExpression expr = (PsiMethodCallExpression)parent.getParent();
            result.add(new FoldingDescriptor(expr.getNode(), expr.getTextRange()));
          }
        }
      }
    });

    return result.toArray(new FoldingDescriptor[result.size()]);
  }



  public String getPlaceholderText(ASTNode node) {
    final PsiElement element = SourceTreeToPsiMap.treeElementToPsi(node);
    if (element instanceof PsiMethodCallExpression) {
      return getI18nMessage((PsiMethodCallExpression)element);
    }
    return element.getText();
  }

  private static String getI18nMessage(PsiMethodCallExpression methodCallExpression) {
    final PsiExpression[] expressions = methodCallExpression.getArgumentList().getExpressions();
    if (expressions.length == 1 && expressions[0] instanceof PsiLiteralExpression) {
      final PsiLiteralExpression literalExpr = (PsiLiteralExpression)expressions[0];
      final Object value = literalExpr.getValue();
      if (value instanceof String) {
        final String key = (String)value;
        final Ref<String> ref = new Ref<String>();
        if (JavaI18nUtil.isValidPropertyReference(literalExpr, key, ref)) {
          final ResourceBundle bundle = ResourceBundle.getBundle(ref.get());
          final String property = (bundle == null) ? null : bundle.getString(key);
          if (property != null) {
            return "\"" + property + "\"";
          }
        }
      }
    }
    return methodCallExpression.getText();
  }

  public boolean isCollapsedByDefault(ASTNode node) {
    return true;
  }
}
