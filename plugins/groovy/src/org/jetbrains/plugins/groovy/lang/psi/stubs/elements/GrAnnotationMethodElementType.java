/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.lang.ASTNode;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.util.io.StringRef;
import org.jetbrains.plugins.groovy.lang.psi.GrStubElementType;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAnnotationMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.members.GrAnnotationMethodImpl;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrAnnotationMethodStub;
import org.jetbrains.plugins.groovy.lang.psi.stubs.impl.GrAnnotationMethodStubImpl;
import org.jetbrains.plugins.groovy.lang.psi.stubs.index.GrAnnotationMethodNameIndex;

import java.io.IOException;

/**
 * @author ilyas
 */
public class GrAnnotationMethodElementType extends GrStubElementType<GrAnnotationMethodStub, GrAnnotationMethod> {

  public GrAnnotationMethodElementType() {
    super("annotation method");
  }

  @Override
  public GrAnnotationMethod createElement(ASTNode node) {
    return new GrAnnotationMethodImpl(node);
  }

  @Override
  public GrAnnotationMethod createPsi(GrAnnotationMethodStub stub) {
    return new GrAnnotationMethodImpl(stub);
  }

  @Override
  public GrAnnotationMethodStub createStub(final GrAnnotationMethod psi, final StubElement parentStub) {
    return new GrAnnotationMethodStubImpl(parentStub, StringRef.fromString(psi.getName()));
  }

  public void serialize(GrAnnotationMethodStub stub, StubOutputStream dataStream) throws IOException {
    dataStream.writeName(stub.getName());
  }

  public GrAnnotationMethodStub deserialize(StubInputStream dataStream, StubElement parentStub) throws IOException {
    StringRef ref = dataStream.readName();
    return new GrAnnotationMethodStubImpl(parentStub, ref);
  }

  public void indexStub(GrAnnotationMethodStub stub, IndexSink sink) {
    String name = stub.getName();
    if (name != null) {
      sink.occurrence(GrAnnotationMethodNameIndex.KEY, name);
    }
  }

}
