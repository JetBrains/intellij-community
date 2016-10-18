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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.annotation.GrAnnotationArgumentListImpl;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrAnnotationArgumentListStub;

import java.io.IOException;

public class GrAnnotationArgumentListElementType extends GrStubElementType<GrAnnotationArgumentListStub, GrAnnotationArgumentList> {

  public GrAnnotationArgumentListElementType() {
    super("annotation arguments");
  }

  @Override
  public boolean isLeftBound() {
    return true;
  }

  @Override
  public void serialize(@NotNull GrAnnotationArgumentListStub stub, @NotNull StubOutputStream dataStream) throws IOException {
  }

  @NotNull
  @Override
  public GrAnnotationArgumentListStub deserialize(@NotNull StubInputStream dataStream, StubElement parentStub) throws IOException {
    return new GrAnnotationArgumentListStub(parentStub);
  }

  @Override
  public GrAnnotationArgumentList createPsi(@NotNull GrAnnotationArgumentListStub stub) {
    return new GrAnnotationArgumentListImpl(stub);
  }

  @NotNull
  @Override
  public GrAnnotationArgumentListStub createStub(@NotNull GrAnnotationArgumentList psi, StubElement parentStub) {
    return new GrAnnotationArgumentListStub(parentStub);
  }
}
