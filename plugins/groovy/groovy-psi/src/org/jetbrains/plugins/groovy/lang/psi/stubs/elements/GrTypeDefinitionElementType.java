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

import com.intellij.psi.PsiNameHelper;
import com.intellij.psi.impl.java.stubs.index.JavaStubIndexKeys;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.util.io.StringRef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrStubUtils;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrTypeDefinitionStub;
import org.jetbrains.plugins.groovy.lang.psi.stubs.index.GrAnnotatedMemberIndex;
import org.jetbrains.plugins.groovy.lang.psi.stubs.index.GrAnonymousClassIndex;
import org.jetbrains.plugins.groovy.lang.psi.stubs.index.GrFullClassNameIndex;

import java.io.IOException;

/**
 * @author ilyas
 */
public abstract class GrTypeDefinitionElementType<TypeDef extends GrTypeDefinition>
  extends GrStubElementType<GrTypeDefinitionStub, TypeDef> {

  public GrTypeDefinitionElementType(@NotNull String debugName) {
    super(debugName);
  }

  @Override
  public GrTypeDefinitionStub createStub(@NotNull TypeDef psi, StubElement parentStub) {
    String[] superClassNames = psi.getSuperClassNames();
    final byte flags = GrTypeDefinitionStub.buildFlags(psi);
    return new GrTypeDefinitionStub(parentStub, psi.getName(), superClassNames, this, psi.getQualifiedName(), GrStubUtils
      .getAnnotationNames(psi),
                                        flags);
  }

  @Override
  public void serialize(@NotNull GrTypeDefinitionStub stub, @NotNull StubOutputStream dataStream) throws IOException {
    dataStream.writeName(stub.getName());
    dataStream.writeName(stub.getQualifiedName());
    dataStream.writeByte(stub.getFlags());
    writeStringArray(dataStream, stub.getSuperClassNames());
    writeStringArray(dataStream, stub.getAnnotations());
  }

  private static void writeStringArray(StubOutputStream dataStream, String[] names) throws IOException {
    dataStream.writeByte(names.length);
    for (String name : names) {
      dataStream.writeName(name);
    }
  }

  @Override
  @NotNull
  public GrTypeDefinitionStub deserialize(@NotNull StubInputStream dataStream, StubElement parentStub) throws IOException {
    String name = StringRef.toString(dataStream.readName());
    String qname = StringRef.toString(dataStream.readName());
    byte flags = dataStream.readByte();
    String[] superClasses = readStringArray(dataStream);
    String[] annos = readStringArray(dataStream);
    return new GrTypeDefinitionStub(parentStub, name, superClasses, this, qname, annos, flags);
  }

  private static String[] readStringArray(StubInputStream dataStream) throws IOException {
    byte supersNumber = dataStream.readByte();
    String[] superClasses = new String[supersNumber];
    for (int i = 0; i < supersNumber; i++) {
      superClasses[i] = StringRef.toString(dataStream.readName());
    }
    return superClasses;
  }

  @Override
  public void indexStub(@NotNull GrTypeDefinitionStub stub, @NotNull IndexSink sink) {
    if (stub.isAnonymous()) {
      final String[] classNames = stub.getSuperClassNames();
      if (classNames.length != 1) return;
      final String baseClassName = classNames[0];
      if (baseClassName != null) {
        final String shortName = PsiNameHelper.getShortClassName(baseClassName);
        sink.occurrence(GrAnonymousClassIndex.KEY, shortName);
      }
    }
    else {
      String shortName = stub.getName();
      if (shortName != null) {
        sink.occurrence(JavaStubIndexKeys.CLASS_SHORT_NAMES, shortName);
      }
      final String fqn = stub.getQualifiedName();
      if (fqn != null) {
        sink.occurrence(GrFullClassNameIndex.KEY, fqn.hashCode());
      }
    }

    for (String annName : stub.getAnnotations()) {
      if (annName != null) {
        sink.occurrence(GrAnnotatedMemberIndex.KEY, annName);
      }
    }
  }
}
