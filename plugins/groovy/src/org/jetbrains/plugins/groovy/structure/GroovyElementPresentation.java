package org.jetbrains.plugins.groovy.structure;

import com.intellij.psi.*;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;

/**
 * User: Dmitry.Krasilschikov
 * Date: 21.05.2007
 */
public class GroovyElementPresentation {
//  public static String getPresentableText(GroovyPsiElement element) {
//    assert element != null;
//    StringBuffer presentableText = new StringBuffer();
//
//    /*if (element instanceof GroovyFileBase) {
//      VirtualFile virtualFile = ((GroovyFileBase) element).getVirtualFile();
//      assert virtualFile != null;
//      presentableText.append(virtualFile.getName());
//
//    }*/ /*else if (element instanceof GrTypeDefinition) {
//      presentableText.append(((GrTypeDefinition) element).getNameIdentifierGroovy().getText());
//
//    }*/ /*else if (element instanceof GrMethod) {
//      GrMethod grMethod = (GrMethod) element;
//      presentableText.append(grMethod.getNameIdentifierGroovy().getText());
//      presentableText.append(" ");
//
//      PsiParameterList paramList = grMethod.getParameterList();
//      PsiParameter[] parameters = paramList.getParameters();
//
//      presentableText.append("(");
//      for (int i = 0; i < parameters.length; i++) {
//        if (i > 0) presentableText.append(", ");
//        presentableText.append(parameters[i].getContainingClass().getPresentableText());
//      }
//      presentableText.append(")");
//
//      GrTypeElement returnType = grMethod.getReturnTypeElementGroovy();
//
//      if (returnType != null) {
//        presentableText.append(":");
//        presentableText.append(returnType.getContainingClass().getPresentableText());
//      }
//    }
//*/
//    return presentableText.toString();
//  }

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

  public static String getVariablePresentableText(GrVariableDeclaration varDecls, String variableName) {
    assert varDecls != null;
    StringBuffer presentableText = new StringBuffer();

    presentableText.append(variableName);
    GrTypeElement varTypeElement = varDecls.getTypeElementGroovy();

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
}