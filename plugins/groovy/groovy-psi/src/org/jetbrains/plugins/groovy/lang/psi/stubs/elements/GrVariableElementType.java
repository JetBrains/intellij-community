// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.stubs.elements;

import com.intellij.lang.ASTNode;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.util.io.StringRef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.parser.GroovyStubElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.GrVariableImpl;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrStubUtils;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrVariableStub;

import java.io.IOException;

public final class GrVariableElementType extends GrStubElementType<GrVariableStub, GrVariable> {

  public GrVariableElementType(String debugName) {
    super(debugName);
  }

  @Override
  public void serialize(@NotNull GrVariableStub stub, @NotNull StubOutputStream dataStream) throws IOException {
    dataStream.writeName(stub.getName());
    GrStubUtils.writeStringArray(dataStream, stub.getAnnotations());
    GrStubUtils.writeNullableString(dataStream, stub.getTypeText());
  }

  @Override
  public GrVariable createPsi(@NotNull GrVariableStub stub) {
    return new GrVariableImpl(stub);
  }

  @Override
  public @NotNull GrVariableStub createStub(@NotNull GrVariable psi, StubElement parentStub) {
    return new GrVariableStub(
      parentStub,
      this,
      StringRef.fromNullableString(psi.getName()),
      GrStubUtils.getAnnotationNames(psi),
      GrStubUtils.getTypeText(psi.getTypeElementGroovy())
    );
  }

  @Override
  public @NotNull GrVariableStub deserialize(@NotNull StubInputStream dataStream, StubElement parentStub) throws IOException {
    final StringRef name = dataStream.readName();
    final String[] annNames = GrStubUtils.readStringArray(dataStream);
    final String typeText = GrStubUtils.readNullableString(dataStream);
    return new GrVariableStub(parentStub, this, name, annNames, typeText);
  }

  @Override
  public boolean shouldCreateStub(ASTNode node) {
    ASTNode parent = node.getTreeParent();
    return parent != null &&
           parent.getElementType() == GroovyStubElementTypes.VARIABLE_DECLARATION &&
           GroovyStubElementTypes.VARIABLE_DECLARATION.shouldCreateStub(parent);
  }
}
