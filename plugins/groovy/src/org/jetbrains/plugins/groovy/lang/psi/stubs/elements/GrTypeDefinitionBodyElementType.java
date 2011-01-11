package org.jetbrains.plugins.groovy.lang.psi.stubs.elements;

import com.intellij.lang.ASTNode;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinitionBody;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.bodies.GrTypeDefinitionBodyImpl;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrTypeDefinitionBodyStub;

import java.io.IOException;

/**
* @author peter
*/
public class GrTypeDefinitionBodyElementType extends GrStubElementType<GrTypeDefinitionBodyStub, GrTypeDefinitionBody> {
  private final boolean myShouldCreateStub;

  public GrTypeDefinitionBodyElementType(final String debugName, final boolean shouldCreateStub) {
    super(debugName);
    myShouldCreateStub = shouldCreateStub;
  }

  @Override
  public boolean shouldCreateStub(ASTNode node) {
    return myShouldCreateStub;
  }

  @Override
  public GrTypeDefinitionBody createPsi(GrTypeDefinitionBodyStub stub) {
    return new GrTypeDefinitionBodyImpl(stub);
  }

  @Override
  public GrTypeDefinitionBodyStub createStub(GrTypeDefinitionBody psi, StubElement parentStub) {
    return new GrTypeDefinitionBodyStub(parentStub);
  }

  @Override
  public void serialize(GrTypeDefinitionBodyStub stub, StubOutputStream dataStream) throws IOException {
  }

  @Override
  public GrTypeDefinitionBodyStub deserialize(StubInputStream dataStream, StubElement parentStub) throws IOException {
    return new GrTypeDefinitionBodyStub(parentStub);
  }
}
