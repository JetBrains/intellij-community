/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.annotation.GrAnnotationImpl;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrAnnotationStub;

import java.io.IOException;

/**
 * Created by Max Medvedev on 12/6/13
 */
public class GrAnnotationElementType extends GrStubElementType<GrAnnotationStub, GrAnnotation> {
  public GrAnnotationElementType(@NotNull String name) {
    super(name);
  }

  @Override
  public GrAnnotation createPsi(@NotNull GrAnnotationStub stub) {
    return new GrAnnotationImpl(stub);
  }

  @Override
  public GrAnnotationStub createStub(@NotNull GrAnnotation psi, StubElement parentStub) {
    return new GrAnnotationStub(parentStub, psi);
  }

  @Override
  public void serialize(@NotNull GrAnnotationStub stub, @NotNull StubOutputStream dataStream) throws IOException {
    dataStream.writeUTFFast(stub.getText());
  }

  @NotNull
  @Override
  public GrAnnotationStub deserialize(@NotNull StubInputStream dataStream, StubElement parentStub) throws IOException {
    return new GrAnnotationStub(parentStub, dataStream.readUTFFast());
  }
}
