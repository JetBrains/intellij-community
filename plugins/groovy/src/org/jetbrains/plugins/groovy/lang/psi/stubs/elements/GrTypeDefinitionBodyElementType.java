package org.jetbrains.plugins.groovy.lang.psi.stubs.elements;

import com.intellij.lang.ASTNode;
import com.intellij.psi.stubs.EmptyStub;
import com.intellij.psi.stubs.EmptyStubElementType;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinitionBody;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.bodies.GrTypeDefinitionBodyImpl;

/**
* @author peter
*/
public class GrTypeDefinitionBodyElementType extends EmptyStubElementType<GrTypeDefinitionBody> {
  private final boolean myShouldCreateStub;

  public GrTypeDefinitionBodyElementType(final String debugName, final boolean shouldCreateStub) {
    super(debugName, GroovyFileType.GROOVY_LANGUAGE);
    myShouldCreateStub = shouldCreateStub;
  }

  @Override
  public boolean shouldCreateStub(ASTNode node) {
    return myShouldCreateStub;
  }

  @Override
  public GrTypeDefinitionBody createPsi(EmptyStub stub) {
    return new GrTypeDefinitionBodyImpl(stub, GroovyElementTypes.CLASS_BODY);
  }

}
