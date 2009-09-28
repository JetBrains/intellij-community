package org.jetbrains.plugins.groovy.lang.psi.stubs.elements;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrInterfaceDefinition;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.GrInterfaceDefinitionImpl;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrTypeDefinitionStub;

/**
 * @author ilyas
 */
public class GrInterfaceDefinitionElementType extends GrTypeDefinitionElementType<GrInterfaceDefinition>{

  public GrInterfaceDefinition createPsi(GrTypeDefinitionStub stub) {
    return new GrInterfaceDefinitionImpl(stub);
  }

  public GrInterfaceDefinitionElementType() {
    super("interface definition");
  }

  public PsiElement createElement(ASTNode node) {
    return new GrInterfaceDefinitionImpl(node);
  }
}