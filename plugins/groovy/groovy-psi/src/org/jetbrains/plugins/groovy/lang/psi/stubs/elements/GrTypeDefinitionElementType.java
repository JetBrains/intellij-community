// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

import static org.jetbrains.plugins.groovy.lang.psi.stubs.GrStubUtils.readStringArray;
import static org.jetbrains.plugins.groovy.lang.psi.stubs.GrStubUtils.writeStringArray;

/**
 * @author ilyas
 */
public abstract class GrTypeDefinitionElementType<TypeDef extends GrTypeDefinition>
  extends GrStubElementType<GrTypeDefinitionStub, TypeDef> {

  public GrTypeDefinitionElementType(@NotNull String debugName) {
    super(debugName);
  }

  @NotNull
  @Override
  public GrTypeDefinitionStub createStub(@NotNull TypeDef psi, StubElement parentStub) {
    final byte flags = GrTypeDefinitionStub.buildFlags(psi);
    return new GrTypeDefinitionStub(
      parentStub, psi.getName(),
      GrStubUtils.getBaseClassName(psi),
      this, psi.getQualifiedName(),
      GrStubUtils.getAnnotationNames(psi),
      flags
    );
  }

  @Override
  public void serialize(@NotNull GrTypeDefinitionStub stub, @NotNull StubOutputStream dataStream) throws IOException {
    dataStream.writeName(stub.getName());
    dataStream.writeName(stub.getQualifiedName());
    dataStream.writeByte(stub.getFlags());
    dataStream.writeName(stub.getBaseClassName());
    writeStringArray(dataStream, stub.getAnnotations());
  }

  @Override
  @NotNull
  public GrTypeDefinitionStub deserialize(@NotNull StubInputStream dataStream, StubElement parentStub) throws IOException {
    String name = dataStream.readNameString();
    String qname = dataStream.readNameString();
    byte flags = dataStream.readByte();
    String baseClassName = dataStream.readNameString();
    String[] annos = readStringArray(dataStream);
    return new GrTypeDefinitionStub(parentStub, name, baseClassName, this, qname, annos, flags);
  }

  @Override
  public void indexStub(@NotNull GrTypeDefinitionStub stub, @NotNull IndexSink sink) {
    if (stub.isAnonymous()) {
      final String baseClassName = stub.getBaseClassName();
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
