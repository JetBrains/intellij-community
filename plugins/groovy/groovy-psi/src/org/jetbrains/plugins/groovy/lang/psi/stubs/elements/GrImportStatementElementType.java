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

import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.toplevel.imports.GrImportStatementImpl;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrImportStatementStub;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrStubUtils;

import java.io.IOException;

/**
 * Created by Max Medvedev on 11/29/13
 */
public class GrImportStatementElementType extends GrStubElementType<GrImportStatementStub, GrImportStatement> {

  public GrImportStatementElementType(String debugName) {
    super(debugName);
  }

  @Override
  public GrImportStatement createPsi(@NotNull GrImportStatementStub stub) {
    return new GrImportStatementImpl(stub, this);
  }

  @Override
  public GrImportStatementStub createStub(@NotNull GrImportStatement psi, StubElement parentStub) {
    GrCodeReferenceElement ref = psi.getImportReference();
    return new GrImportStatementStub(parentStub,
                                     this,
                                     ref != null ? ref.getText() : null,
                                     psi.isAliasedImport() ? psi.getImportedName() : null,
                                     GrImportStatementStub.buildFlags(psi.isStatic(), psi.isOnDemand())
    );
  }

  @Override
  public void serialize(@NotNull GrImportStatementStub stub, @NotNull StubOutputStream dataStream) throws IOException {
    GrStubUtils.writeNullableString(dataStream, stub.getReferenceText());
    GrStubUtils.writeNullableString(dataStream, stub.getAliasName());
    dataStream.writeByte(stub.getFlags());
  }

  @NotNull
  @Override
  public GrImportStatementStub deserialize(@NotNull StubInputStream dataStream, StubElement parentStub) throws IOException {
    String referenceText = GrStubUtils.readNullableString(dataStream);
    String aliasName = GrStubUtils.readNullableString(dataStream);
    byte flags = dataStream.readByte();

    return new GrImportStatementStub(parentStub, this, referenceText, aliasName, flags);
  }
}
