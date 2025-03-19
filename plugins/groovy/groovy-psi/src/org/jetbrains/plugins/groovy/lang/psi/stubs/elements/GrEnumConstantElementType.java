// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.stubs.elements;

import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.io.StringRef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.parser.GroovyStubElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstant;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.enumConstant.GrEnumConstantImpl;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrFieldStub;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrStubUtils;

import java.io.IOException;

public class GrEnumConstantElementType extends GrStubElementType<GrFieldStub, GrEnumConstant> {

  public GrEnumConstantElementType(String debugName) {
    super(debugName);
  }

  @Override
  public GrEnumConstant createPsi(@NotNull GrFieldStub stub) {
    return new GrEnumConstantImpl(stub);
  }

  @Override
  public @NotNull GrFieldStub createStub(@NotNull GrEnumConstant psi, StubElement parentStub) {
    String[] annNames = GrStubUtils.getAnnotationNames(psi);
    return new GrFieldStub(parentStub, StringRef.fromString(psi.getName()), annNames, ArrayUtilRt.EMPTY_STRING_ARRAY,
                           GroovyStubElementTypes.ENUM_CONSTANT, GrFieldStub.buildFlags(psi), null);
  }

  @Override
  public void serialize(@NotNull GrFieldStub stub, @NotNull StubOutputStream dataStream) throws IOException {
    serializeFieldStub(stub, dataStream);
  }

  @Override
  public @NotNull GrFieldStub deserialize(@NotNull StubInputStream dataStream, StubElement parentStub) throws IOException {
    return GrFieldElementType.deserializeFieldStub(dataStream, parentStub);
  }

  protected static void serializeFieldStub(GrFieldStub stub, StubOutputStream dataStream) throws IOException {
    GrFieldElementType.serializeFieldStub(stub, dataStream);
  }

  @Override
  public void indexStub(@NotNull GrFieldStub stub, @NotNull IndexSink sink) {
    GrFieldElementType.indexFieldStub(stub, sink);
  }
}
