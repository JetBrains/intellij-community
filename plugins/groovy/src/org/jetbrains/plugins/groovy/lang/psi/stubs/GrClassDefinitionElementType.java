package org.jetbrains.plugins.groovy.lang.psi.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.StubElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrClassDefinition;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.GrClassDefinitionImpl;

/**
 * @author ilyas
 */
public class GrClassDefinitionElementType extends GrTypeDefinitionElementType<GrClassDefinition>{

  public GrClassDefinition createPsi(GrTypeDefinitionStub stub) {
    return new GrClassDefinitionImpl(stub);
  }

  public GrClassDefinitionElementType() {
    super("class definition");
  }

  public PsiElement createElement(ASTNode node) {
    return new GrClassDefinitionImpl(node);
  }
}
