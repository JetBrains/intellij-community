package org.jetbrains.plugins.groovy.structure;

import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;

/**
 * User: Dmitry.Krasilschikov
 * Date: 21.05.2007
 */
public class GroovyElementPresentation {
  public static String getPresentableText(GroovyPsiElement element) {
    assert element != null;
    StringBuffer presentableText = new StringBuffer();

    if (element instanceof GroovyFile) {
      VirtualFile virtualFile = ((GroovyFile) element).getVirtualFile();
      assert virtualFile != null;
      presentableText.append(virtualFile.getName());

    } else if (element instanceof GrTypeDefinition) {
      presentableText.append(((GrTypeDefinition) element).getNameIdentifierGroovy().getText());

    } else if (element instanceof GrMethod) {
      GrMethod grMethod = (GrMethod) element;
      presentableText.append(grMethod.getNameIdentifierGroovy().getText());
      presentableText.append(" ");

      PsiParameterList paramList = grMethod.getParameterList();
      PsiParameter[] parameters = paramList.getParameters();

      presentableText.append("(");
      for (PsiParameter parameter : parameters) {
        presentableText.append(parameter.getType().getPresentableText());
      }
      presentableText.append(")");

      GrTypeElement returnType = grMethod.getReturnTypeElementGroovy();

      if (returnType != null) {
        presentableText.append(":");
        presentableText.append(returnType.getType().getPresentableText());
      }
    }
    return presentableText.toString();
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
}