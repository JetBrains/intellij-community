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
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.StringRef;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GrStubElementType;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstant;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.enumConstant.GrEnumConstantImpl;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrFieldStub;
import org.jetbrains.plugins.groovy.lang.psi.stubs.impl.GrFieldStubImpl;

import java.io.IOException;
import java.util.Set;

/**
 * @author ilyas
 */
public class GrEnumConstantElementType extends GrStubElementType<GrFieldStub, GrEnumConstant> {

  public GrEnumConstantElementType() {
    super("Enumeration constant");
  }

  public PsiElement createElement(ASTNode node) {
    return new GrEnumConstantImpl(node);
  }

  public GrEnumConstant createPsi(GrFieldStub stub) {
    return new GrEnumConstantImpl(stub);
  }

  @Override
  public GrFieldStub createStub(GrEnumConstant psi, StubElement parentStub) {
     final GrModifierList list = psi.getModifierList();
    String[] annNames;
    if (list == null) {
      annNames = ArrayUtil.EMPTY_STRING_ARRAY;
    }
    else {
      annNames = ContainerUtil.map(list.getAnnotations(), new Function<GrAnnotation, String>() {
        public String fun(final GrAnnotation grAnnotation) {
          final GrCodeReferenceElement element = grAnnotation.getClassReference();
          if (element == null) return null;
          return element.getReferenceName();
        }
      }, ArrayUtil.EMPTY_STRING_ARRAY);
    }
    return new GrFieldStubImpl(parentStub, StringRef.fromString(psi.getName()), annNames, ArrayUtil.EMPTY_STRING_ARRAY, GroovyElementTypes.ENUM_CONSTANT, GrFieldStubImpl.buildFlags(psi));
  }

  public void serialize(GrFieldStub stub, StubOutputStream dataStream) throws IOException {
    serializeFieldStub(stub, dataStream);
  }

  public GrFieldStub deserialize(StubInputStream dataStream, StubElement parentStub) throws IOException {
    return GrFieldElementType.deserializeFieldStub(dataStream, parentStub);
  }

  protected static void serializeFieldStub(GrFieldStub stub, StubOutputStream dataStream) throws IOException {
    GrFieldElementType.serializeFieldStub(stub, dataStream);
  }

  public void indexStub(GrFieldStub stub, IndexSink sink) {
    GrFieldElementType.indexFieldStub(stub, sink);
  }
}
