package org.jetbrains.plugins.groovy.lang.psi.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrEnumTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.GrClassDefinitionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.GrEnumTypeDefinitionImpl;

/**
 * @author ilyas
 */
public class GrEnumDefinitionElementType extends GrTypeDefinitionElementType<GrEnumTypeDefinition>{

  public GrEnumTypeDefinition createPsi(GrTypeDefinitionStub stub) {
    return new GrEnumTypeDefinitionImpl(stub);
  }

  public GrEnumDefinitionElementType() {
    super("enumeration definition");
  }

  public PsiElement createElement(ASTNode node) {
    return new GrEnumTypeDefinitionImpl(node);
  }
}
