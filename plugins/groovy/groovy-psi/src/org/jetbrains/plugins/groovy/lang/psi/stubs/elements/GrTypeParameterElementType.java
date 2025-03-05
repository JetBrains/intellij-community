// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.stubs.elements;

import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.util.io.StringRef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeParameter;
import org.jetbrains.plugins.groovy.lang.psi.impl.types.GrTypeParameterImpl;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrTypeParameterStub;

import java.io.IOException;

public class GrTypeParameterElementType extends GrStubElementType<GrTypeParameterStub, GrTypeParameter> {

  public GrTypeParameterElementType(String debugName) {
    super(debugName);
  }

  @Override
  public GrTypeParameter createPsi(@NotNull GrTypeParameterStub stub) {
    return new GrTypeParameterImpl(stub);
  }

  @Override
  public @NotNull GrTypeParameterStub createStub(@NotNull GrTypeParameter psi, StubElement parentStub) {
    return new GrTypeParameterStub(parentStub, StringRef.fromString(psi.getName()));
  }

  @Override
  public void serialize(@NotNull GrTypeParameterStub stub, @NotNull StubOutputStream dataStream) throws IOException {
    dataStream.writeName(stub.getName());
  }

  @Override
  public @NotNull GrTypeParameterStub deserialize(@NotNull StubInputStream dataStream, StubElement parentStub) throws IOException {
    return new GrTypeParameterStub(parentStub, dataStream.readName());
  }
}
