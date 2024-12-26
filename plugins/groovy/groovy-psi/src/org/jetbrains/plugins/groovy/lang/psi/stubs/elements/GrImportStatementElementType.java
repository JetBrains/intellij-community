// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.stubs.elements;

import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.impl.toplevel.imports.GrImportStatementImpl;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrImportStatementStub;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrStubUtils;

import java.io.IOException;

public class GrImportStatementElementType extends GrStubElementType<GrImportStatementStub, GrImportStatement> {

  public GrImportStatementElementType(String debugName) {
    super(debugName);
  }

  @Override
  public GrImportStatement createPsi(@NotNull GrImportStatementStub stub) {
    return new GrImportStatementImpl(stub, this);
  }

  @Override
  public @NotNull GrImportStatementStub createStub(@NotNull GrImportStatement psi, StubElement parentStub) {
    return new GrImportStatementStub(
      parentStub, this,
      psi.getImportFqn(),
      psi.isAliasedImport() ? psi.getImportedName() : null,
      GrImportStatementStub.buildFlags(psi.isStatic(), psi.isOnDemand())
    );
  }

  @Override
  public void serialize(@NotNull GrImportStatementStub stub, @NotNull StubOutputStream dataStream) throws IOException {
    GrStubUtils.writeNullableString(dataStream, stub.getFqn());
    GrStubUtils.writeNullableString(dataStream, stub.getAliasName());
    dataStream.writeByte(stub.getFlags());
  }

  @Override
  public @NotNull GrImportStatementStub deserialize(@NotNull StubInputStream dataStream, StubElement parentStub) throws IOException {
    String fqn = GrStubUtils.readNullableString(dataStream);
    String aliasName = GrStubUtils.readNullableString(dataStream);
    byte flags = dataStream.readByte();
    return new GrImportStatementStub(parentStub, this, fqn, aliasName, flags);
  }
}
