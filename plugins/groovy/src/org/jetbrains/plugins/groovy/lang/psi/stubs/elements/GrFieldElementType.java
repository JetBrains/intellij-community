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
import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.StringRef;
import org.jetbrains.plugins.groovy.lang.psi.GrStubElementType;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.GrFieldImpl;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrFieldStub;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrStubUtils;
import org.jetbrains.plugins.groovy.lang.psi.stubs.impl.GrFieldStubImpl;
import org.jetbrains.plugins.groovy.lang.psi.stubs.index.GrAnnotatedMemberIndex;
import org.jetbrains.plugins.groovy.lang.psi.stubs.index.GrFieldNameIndex;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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

  public PsiElement createElement(ASTNode node) {
    return new GrFieldImpl(node);
  }

  public GrField createPsi(GrFieldStub stub) {
    return new GrFieldImpl(stub);
  }

  public GrFieldStub createStub(GrField psi, StubElement parentStub) {
    String[] annNames = GrTypeDefinitionElementType.getAnnotationNames(psi);

    String[] namedParametersArray = ArrayUtil.EMPTY_STRING_ARRAY;
    if (psi instanceof GrFieldImpl){
      namedParametersArray = psi.getNamedParametersArray();
    }

    return new GrFieldStubImpl(parentStub, StringRef.fromString(psi.getName()), annNames, namedParametersArray, FIELD, GrFieldStubImpl.buildFlags(psi));
  }

  public void serialize(GrFieldStub stub, StubOutputStream dataStream) throws IOException {
    serializeFieldStub(stub, dataStream);
  }

  public GrFieldStub deserialize(StubInputStream dataStream, StubElement parentStub) throws IOException {
    return deserializeFieldStub(dataStream, parentStub);
  }

  public void indexStub(GrFieldStub stub, IndexSink sink) {
    indexFieldStub(stub, sink);
  }

  /*
   * ****************************************************************************************************************
   */

  static void serializeFieldStub(GrFieldStub stub, StubOutputStream dataStream) throws IOException {
    dataStream.writeName(stub.getName());
    final String[] annotations = stub.getAnnotations();
    dataStream.writeByte(annotations.length);
    for (String s : annotations) {
      dataStream.writeName(s);
    }

    final String[] namedParameters = stub.getNamedParameters();
    GrStubUtils.writeStringArray(dataStream, namedParameters);

    dataStream.writeByte(stub.getFlags());
  }

  static GrFieldStub deserializeFieldStub(StubInputStream dataStream, StubElement parentStub) throws IOException {
    StringRef ref = dataStream.readName();
    final byte b = dataStream.readByte();
    final String[] annNames = new String[b];
    for (int i = 0; i < b; i++) {
      annNames[i] = dataStream.readName().toString();
    }

    final String[] namedParameters = GrStubUtils.readStringArray(dataStream);

    byte flags = dataStream.readByte();

    return new GrFieldStubImpl(parentStub, ref, annNames, namedParameters, GrFieldStubImpl.isEnumConstant(flags) ? ENUM_CONSTANT : FIELD,
                               flags);
  }

  static void indexFieldStub(GrFieldStub stub, IndexSink sink) {
    String name = stub.getName();
    if (name != null) {
      sink.occurrence(GrFieldNameIndex.KEY, name);
    }
    for (String annName : stub.getAnnotations()) {
      if (annName != null) {
        sink.occurrence(GrAnnotatedMemberIndex.KEY, annName);
      }
    }
  }
}
