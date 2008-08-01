package org.jetbrains.plugins.groovy.lang.psi.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrAnnotationTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.GrAnnotationTypeDefinitionImpl;

/**
 * @author ilyas
 */
public class GrAnnotationDefinitionElementType extends GrTypeDefinitionElementType<GrAnnotationTypeDefinition>{

  public GrAnnotationTypeDefinition createPsi(GrTypeDefinitionStub stub) {
    return new GrAnnotationTypeDefinitionImpl(stub);
  }

  public GrAnnotationDefinitionElementType() {
    super("annotation definition");
  }

  public PsiElement createElement(ASTNode node) {
    return new GrAnnotationTypeDefinitionImpl(node);
  }
}
