package org.jetbrains.plugins.groovy.lang.psi.stubs.elements;

import com.intellij.lang.ASTNode;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.GrVariableDeclarationImpl;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrVariableDeclarationStub;

import java.io.IOException;

/**
* @author peter
*/
public class GrVariableDeclarationElementType extends GrStubElementType<GrVariableDeclarationStub, GrVariableDeclaration> {
  private final boolean myShouldCreateStub;

  public GrVariableDeclarationElementType(final String debugName, final boolean shouldCreateStub) {
    super(debugName);
    myShouldCreateStub = shouldCreateStub;
  }

  @Override
  public boolean shouldCreateStub(ASTNode node) {
    return myShouldCreateStub;
  }

  @Override
  public GrVariableDeclaration createPsi(GrVariableDeclarationStub stub) {
    return new GrVariableDeclarationImpl(stub);
  }

  @Override
  public GrVariableDeclarationStub createStub(GrVariableDeclaration psi, StubElement parentStub) {
    return new GrVariableDeclarationStub(parentStub);
  }

  @Override
  public void serialize(GrVariableDeclarationStub stub, StubOutputStream dataStream) throws IOException {
  }

  @Override
  public GrVariableDeclarationStub deserialize(StubInputStream dataStream, StubElement parentStub) throws IOException {
    return new GrVariableDeclarationStub(parentStub);
  }
}
