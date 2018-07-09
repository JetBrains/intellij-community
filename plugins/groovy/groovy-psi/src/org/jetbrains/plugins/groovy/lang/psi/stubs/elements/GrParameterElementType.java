// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.stubs.elements;

import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.util.io.StringRef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.params.GrParameterImpl;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrParameterStub;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrStubUtils;

import java.io.IOException;

public class GrParameterElementType extends GrStubElementType<GrParameterStub, GrParameter> {

  public GrParameterElementType(String debugName) {
    super(debugName);
  }

  @Override
  public GrParameter createPsi(@NotNull GrParameterStub stub) {
    return new GrParameterImpl(stub);
  }

  @NotNull
  @Override
  public GrParameterStub createStub(@NotNull GrParameter psi, StubElement parentStub) {
    return new GrParameterStub(parentStub, StringRef.fromString(psi.getName()), GrStubUtils.getAnnotationNames(psi),
                               GrStubUtils.getTypeText(
                                 psi.getTypeElementGroovy()),
                               GrParameterStub.encodeFlags(psi.getInitializerGroovy() != null, psi.isVarArgs()));
  }

  @Override
  public void serialize(@NotNull GrParameterStub stub, @NotNull StubOutputStream dataStream) throws IOException {
    dataStream.writeName(stub.getName());
    GrStubUtils.writeStringArray(dataStream, stub.getAnnotations());
    GrStubUtils.writeNullableString(dataStream, stub.getTypeText());
    dataStream.writeVarInt(stub.getFlags());
  }

  @NotNull
  @Override
  public GrParameterStub deserialize(@NotNull StubInputStream dataStream, StubElement parentStub) throws IOException {
    final StringRef name = dataStream.readName();
    final String[] annotations = GrStubUtils.readStringArray(dataStream);
    final String typeText = GrStubUtils.readNullableString(dataStream);
    final int flags = dataStream.readVarInt();
    return new GrParameterStub(parentStub, name, annotations, typeText, flags);
  }
}
