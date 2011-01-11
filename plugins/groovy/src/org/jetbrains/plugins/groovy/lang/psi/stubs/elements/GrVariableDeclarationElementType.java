package org.jetbrains.plugins.groovy.lang.psi.stubs.elements;

import com.intellij.lang.ASTNode;
import com.intellij.psi.stubs.EmptyStub;
import com.intellij.psi.stubs.EmptyStubElementType;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.GrVariableDeclarationImpl;

/**
* @author peter
*/
public class GrVariableDeclarationElementType extends EmptyStubElementType<GrVariableDeclaration> {
  private final boolean myShouldCreateStub;

  public GrVariableDeclarationElementType(final String debugName, final boolean shouldCreateStub) {
    super(debugName, GroovyFileType.GROOVY_LANGUAGE);
    myShouldCreateStub = shouldCreateStub;
  }

  @Override
  public boolean shouldCreateStub(ASTNode node) {
    return myShouldCreateStub;
  }

  @Override
  public GrVariableDeclaration createPsi(EmptyStub stub) {
    return new GrVariableDeclarationImpl(stub);
  }

}
