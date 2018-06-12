// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.stubs.elements;

import com.intellij.psi.impl.java.stubs.index.JavaStubIndexKeys;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.util.ArrayUtil;
import com.intellij.util.io.StringRef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.members.GrMethodImpl;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrMethodStub;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrStubUtils;
import org.jetbrains.plugins.groovy.lang.psi.stubs.index.GrAnnotatedMemberIndex;
import org.jetbrains.plugins.groovy.lang.psi.stubs.index.GrMethodNameIndex;

import java.io.IOException;
import java.util.Set;

/**
 * @author ilyas
 */
public class GrMethodElementType extends GrStubElementType<GrMethodStub, GrMethod> {

  public GrMethodElementType(final String debugName) {
    super(debugName);
  }

  @NotNull
  @Override
  public GrMethodStub createStub(@NotNull GrMethod psi, StubElement parentStub) {

    Set<String> namedParameters = psi.getNamedParameters().keySet();
    return new GrMethodStub(parentStub, StringRef.fromString(psi.getName()), GrStubUtils.getAnnotationNames(psi),
                            ArrayUtil.toStringArray(namedParameters), this,
                            GrStubUtils.getTypeText(psi.getReturnTypeElementGroovy()),
                            GrMethodStub.buildFlags(psi));
  }

  @Override
  public void serialize(@NotNull GrMethodStub stub, @NotNull StubOutputStream dataStream) throws IOException {
    dataStream.writeName(stub.getName());
    GrStubUtils.writeStringArray(dataStream, stub.getAnnotations());
    GrStubUtils.writeStringArray(dataStream, stub.getNamedParameters());
    GrStubUtils.writeNullableString(dataStream, stub.getTypeText());
    dataStream.writeByte(stub.getFlags());
  }

  @Override
  @NotNull
  public GrMethodStub deserialize(@NotNull StubInputStream dataStream, StubElement parentStub) throws IOException {
    StringRef ref = dataStream.readName();
    final String[] annNames = GrStubUtils.readStringArray(dataStream);
    String[] namedParameters = GrStubUtils.readStringArray(dataStream);
    String typeText = GrStubUtils.readNullableString(dataStream);
    final byte flags = dataStream.readByte();
    return new GrMethodStub(parentStub, ref, annNames, namedParameters, this, typeText, flags);
  }

  @Override
  public void indexStub(@NotNull GrMethodStub stub, @NotNull IndexSink sink) {
    String name = stub.getName();
    sink.occurrence(GrMethodNameIndex.KEY, name);
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

  @Override
  public GrMethod createPsi(@NotNull GrMethodStub stub) {
    return new GrMethodImpl(stub);
  }
}
