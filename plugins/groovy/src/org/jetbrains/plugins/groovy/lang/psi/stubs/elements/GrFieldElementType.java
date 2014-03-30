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

import com.intellij.psi.impl.java.stubs.index.JavaStubIndexKeys;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.util.io.StringRef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.GrFieldImpl;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrFieldStub;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrStubUtils;
import org.jetbrains.plugins.groovy.lang.psi.stubs.index.GrAnnotatedMemberIndex;
import org.jetbrains.plugins.groovy.lang.psi.stubs.index.GrFieldNameIndex;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;

import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.ENUM_CONSTANT;
import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.FIELD;

/**
 * @author ilyas
 */
public class GrFieldElementType extends GrStubElementType<GrFieldStub, GrField> {

  public GrFieldElementType() {
    super("field");
  }

  public GrField createPsi(@NotNull GrFieldStub stub) {
    return new GrFieldImpl(stub);
  }

  public GrFieldStub createStub(@NotNull GrField psi, StubElement parentStub) {
    String[] annNames = GrStubUtils.getAnnotationNames(psi);

    Set<String> namedParameters = Collections.emptySet();
    if (psi instanceof GrFieldImpl){
      namedParameters = psi.getNamedParameters().keySet();
    }

    return new GrFieldStub(parentStub, StringRef.fromString(psi.getName()), annNames, namedParameters.toArray(new String[namedParameters.size()]), FIELD, GrFieldStub.buildFlags(psi),
                           GrStubUtils.getTypeText(psi.getTypeElementGroovy()));
  }

  public void serialize(@NotNull GrFieldStub stub, @NotNull StubOutputStream dataStream) throws IOException {
    serializeFieldStub(stub, dataStream);
  }

  @NotNull
  public GrFieldStub deserialize(@NotNull StubInputStream dataStream, StubElement parentStub) throws IOException {
    return deserializeFieldStub(dataStream, parentStub);
  }

  public void indexStub(@NotNull GrFieldStub stub, @NotNull IndexSink sink) {
    indexFieldStub(stub, sink);
  }

  /*
   * ****************************************************************************************************************
   */

  static void serializeFieldStub(GrFieldStub stub, StubOutputStream dataStream) throws IOException {
    dataStream.writeName(stub.getName());
    GrStubUtils.writeStringArray(dataStream, stub.getAnnotations());
    GrStubUtils.writeStringArray(dataStream, stub.getNamedParameters());
    dataStream.writeByte(stub.getFlags());
    GrStubUtils.writeNullableString(dataStream, stub.getTypeText());
  }

  static GrFieldStub deserializeFieldStub(StubInputStream dataStream, StubElement parentStub) throws IOException {
    StringRef ref = dataStream.readName();
    final String[] annNames = GrStubUtils.readStringArray(dataStream);
    final String[] namedParameters = GrStubUtils.readStringArray(dataStream);
    byte flags = dataStream.readByte();
    final String typeText = GrStubUtils.readNullableString(dataStream);
    return new GrFieldStub(parentStub, ref, annNames, namedParameters, GrFieldStub.isEnumConstant(flags) ? ENUM_CONSTANT : FIELD,
                               flags, typeText);
  }


  static void indexFieldStub(GrFieldStub stub, IndexSink sink) {
    String name = stub.getName();
    sink.occurrence(GrFieldNameIndex.KEY, name);
    if (GrStubUtils.isGroovyStaticMemberStub(stub)) {
      sink.occurrence(JavaStubIndexKeys.JVM_STATIC_MEMBERS_NAMES, name);
      sink.occurrence(JavaStubIndexKeys.JVM_STATIC_MEMBERS_TYPES, GrStubUtils.getShortTypeText(stub.getTypeText()));
    }
    for (String annName : stub.getAnnotations()) {
      if (annName != null) {
        sink.occurrence(GrAnnotatedMemberIndex.KEY, annName);
      }
    }
  }
}
