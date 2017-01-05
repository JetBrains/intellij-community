/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.lang.psi.stubs.elements;

import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.util.io.StringRef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationMemberValue;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationNameValuePair;
import org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.annotation.GrAnnotationNameValuePairImpl;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrNameValuePairStub;

import java.io.IOException;

@SuppressWarnings("Duplicates")
public class GrNameValuePairElementType extends GrStubElementType<GrNameValuePairStub, GrAnnotationNameValuePair> {

  public GrNameValuePairElementType() {
    super("Annotation name value pair");
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
      StringRef.toString(dataStream.readName()),
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
