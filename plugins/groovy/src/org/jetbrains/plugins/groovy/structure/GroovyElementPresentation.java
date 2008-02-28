package org.jetbrains.plugins.groovy.structure;

import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiType;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;

/**
 * User: Dmitry.Krasilschikov
 * Date: 21.05.2007
 */
public class GroovyElementPresentation {
  public static String getPresentableText(GroovyPsiElement element) {
    assert element != null;

    if (element instanceof GroovyFileBase) {
      return getFilePresentableText(((GroovyFileBase) element));

    } else if (element instanceof GrTypeDefinition) {
      return getTypeDefinitionPresentableText(((GrTypeDefinition) element));

    } else if (element instanceof GrMethod) {
      return getMethodPresentableText(((GrMethod) element));
    } else {
      return element.getText();
    }
  }

  public static String getVariablePresentableText(GrVariable variable) {
    StringBuffer presentableText = new StringBuffer();

    presentableText.append(variable.getName());
    GrTypeElement varTypeElement = variable.getTypeElementGroovy();

    if (varTypeElement != null) {
      PsiType varType = varTypeElement.getType();
      presentableText.append(":");
      presentableText.append(varType.getPresentableText());
    }
    return presentableText.toString();
  }

  public static String getTypeDefinitionPresentableText(GrTypeDefinition typeDefinition) {
    return typeDefinition.getNameIdentifierGroovy().getText();
  }

  public static String getMethodPresentableText(PsiMethod method) {
    StringBuffer presentableText = new StringBuffer();
    presentableText.append(method.getName());
    presentableText.append(" ");

    PsiParameterList paramList = method.getParameterList();
    PsiParameter[] parameters = paramList.getParameters();

    presentableText.append("(");
    for (int i = 0; i < parameters.length; i++) {
      if (i > 0) presentableText.append(", ");
      presentableText.append(parameters[i].getType().getPresentableText());
    }
    presentableText.append(")");

    PsiType returnType = method.getReturnType();

    if (returnType != null) {
      presentableText.append(":");
      presentableText.append(returnType.getPresentableText());
    }

    return presentableText.toString();
  }

  public static String getFilePresentableText(GroovyFileBase file) {
    return file.getName();
  }

  public static String getExpressionPresentableText(GrExpression expression) {
    if (expression instanceof GrLiteral) {
      final Object value = ((GrLiteral) expression).getValue();
      if (value == null || value.toString().length() == 0) return "\"\"";
    }

    return expression.getText();
  }
}