// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.stubs.elements;

import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationMemberValue;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationNameValuePair;
import org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.annotation.GrAnnotationNameValuePairImpl;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrNameValuePairStub;

import java.io.IOException;

@SuppressWarnings("Duplicates")
public class GrNameValuePairElementType extends GrStubElementType<GrNameValuePairStub, GrAnnotationNameValuePair> {

  public GrNameValuePairElementType(String debugName) {
    super(debugName);
  }

  @Override
  public void serialize(@NotNull GrNameValuePairStub stub, @NotNull StubOutputStream dataStream) throws IOException {
    dataStream.writeName(stub.getName());
    String value = stub.getValue();
    boolean hasValue = value != null;
    dataStream.writeBoolean(hasValue);
    if (hasValue) {
      dataStream.writeUTFFast(value);
    }
  }

  @NotNull
  @Override
  public GrNameValuePairStub deserialize(@NotNull StubInputStream dataStream, StubElement parentStub) throws IOException {
    return new GrNameValuePairStub(
      parentStub,
      dataStream.readNameString(),
      dataStream.readBoolean() ? dataStream.readUTFFast() : null
    );
  }

  @Override
  public GrAnnotationNameValuePair createPsi(@NotNull GrNameValuePairStub stub) {
    return new GrAnnotationNameValuePairImpl(stub);
  }

  @NotNull
  @Override
  public GrNameValuePairStub createStub(@NotNull GrAnnotationNameValuePair psi, StubElement parentStub) {
    String name = psi.getName();
    GrAnnotationMemberValue value = psi.getValue();
    return new GrNameValuePairStub(parentStub, name, value == null ? null : value.getText());
  }
}
