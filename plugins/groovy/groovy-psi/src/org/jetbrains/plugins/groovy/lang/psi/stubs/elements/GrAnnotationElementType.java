// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.stubs.elements;

import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.annotation.GrAnnotationImpl;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrAnnotationStub;

import java.io.IOException;

public class GrAnnotationElementType extends GrStubElementType<GrAnnotationStub, GrAnnotation> {
  public GrAnnotationElementType(@NotNull String name) {
    super(name);
  }

  @Override
  public GrAnnotation createPsi(@NotNull GrAnnotationStub stub) {
    return new GrAnnotationImpl(stub);
  }

  @Override
  public @NotNull GrAnnotationStub createStub(@NotNull GrAnnotation psi, StubElement parentStub) {
    return new GrAnnotationStub(parentStub, psi);
  }

  @Override
  public void serialize(@NotNull GrAnnotationStub stub, @NotNull StubOutputStream dataStream) throws IOException {
    dataStream.writeUTFFast(stub.getText());
  }

  @Override
  public @NotNull GrAnnotationStub deserialize(@NotNull StubInputStream dataStream, StubElement parentStub) throws IOException {
    return new GrAnnotationStub(parentStub, dataStream.readUTFFast());
  }
}
