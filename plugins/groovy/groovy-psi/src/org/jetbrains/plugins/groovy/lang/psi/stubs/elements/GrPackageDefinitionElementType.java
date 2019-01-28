// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.stubs.elements;

import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.util.io.StringRef;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.parser.GroovyStubElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.packaging.GrPackageDefinition;
import org.jetbrains.plugins.groovy.lang.psi.impl.toplevel.packaging.GrPackageDefinitionImpl;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrPackageDefinitionStub;

import java.io.IOException;

public class GrPackageDefinitionElementType extends GrStubElementType<GrPackageDefinitionStub, GrPackageDefinition> {
  public GrPackageDefinitionElementType(@NonNls @NotNull String debugName) {
    super(debugName);
  }

  @Override
  public GrPackageDefinition createPsi(@NotNull GrPackageDefinitionStub stub) {
    return new GrPackageDefinitionImpl(stub);
  }

  @NotNull
  @Override
  public GrPackageDefinitionStub createStub(@NotNull GrPackageDefinition psi, StubElement parentStub) {
    return new GrPackageDefinitionStub(parentStub, GroovyStubElementTypes.PACKAGE_DEFINITION, StringRef.fromString(psi.getPackageName()));
  }

  @Override
  public void serialize(@NotNull GrPackageDefinitionStub stub, @NotNull StubOutputStream dataStream) throws IOException {
    dataStream.writeName(stub.getPackageName());
  }

  @NotNull
  @Override
  public GrPackageDefinitionStub deserialize(@NotNull StubInputStream dataStream, StubElement parentStub) throws IOException {
    return new GrPackageDefinitionStub(parentStub, GroovyStubElementTypes.PACKAGE_DEFINITION, dataStream.readName());
  }
}
