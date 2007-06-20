package org.jetbrains.plugins.groovy.lang.documentation;

import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.psi.*;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

/**
 * @author ven
 */
public class GroovyDocumentationProvider implements DocumentationProvider {
  @Nullable
  public String getQuickNavigateInfo(PsiElement element) {
    if (element instanceof GrVariable) {
      GrVariable variable = (GrVariable) element;
      StringBuffer buffer = new StringBuffer();
      buffer.append(variable.getType().getCanonicalText());
      buffer.append(" ");
      buffer.append(variable.getName());
      return buffer.toString();
    }
    else if (element instanceof GrReferenceExpression) {
      GrReferenceExpression refExpr = (GrReferenceExpression) element;
      StringBuffer buffer = new StringBuffer();
      PsiType type = null;
      if (refExpr.getParent() instanceof GrAssignmentExpression) {
        GrAssignmentExpression assignment = (GrAssignmentExpression) refExpr.getParent();
        if (refExpr.equals(assignment.getLValue())) {
          GrExpression rvalue = assignment.getRValue();
          if (rvalue != null) {
            type = rvalue.getType();
          }
        }
      }
      appendTypeString(buffer, type);
      buffer.append(" ");
      buffer.append(refExpr.getReferenceName());
      return buffer.toString();
    } else if (element instanceof PsiMethod) {
      PsiMethod method = (PsiMethod) element;
      StringBuffer buffer = new StringBuffer();
      if (!method.isConstructor()) {
        appendTypeString(buffer, method.getReturnType());
        buffer.append(" ");
      }
      buffer.append(method.getName()).append(" ");
      buffer.append("(");
      PsiParameter[] parameters = method.getParameterList().getParameters();
      for (int i = 0; i < parameters.length; i++) {
        PsiParameter parameter = parameters[i];
        if (i > 0) buffer.append(", ");
        appendTypeString(buffer, parameter.getType());
        buffer.append(" ");
        buffer.append(parameter.getName());
      }
      buffer.append(")");
      return buffer.toString();
    }

    //todo
    return null;
  }

  private void appendTypeString(StringBuffer buffer, PsiType type) {
    if (type != null) {
      buffer.append(type.getCanonicalText());
    } else {
      buffer.append("untyped");
    }
  }

  @Nullable
  public String getUrlFor(PsiElement element, PsiElement originalElement) {
    return null;
  }

  @Nullable
  public String generateDoc(PsiElement element, PsiElement originalElement) {
    //todo
    return null;
  }

  @Nullable
  public PsiElement getDocumentationElementForLookupItem(PsiManager psiManager, Object object, PsiElement element) {
    return null;
  }

  @Nullable
  public PsiElement getDocumentationElementForLink(PsiManager psiManager, String link, PsiElement context) {
    return null;
  }
}
