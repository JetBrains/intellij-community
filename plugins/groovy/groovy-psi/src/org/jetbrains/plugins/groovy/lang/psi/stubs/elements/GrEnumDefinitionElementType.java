// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.stubs.elements;

import com.intellij.psi.stubs.StubElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrEnumTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.GrEnumTypeDefinitionImpl;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrTypeDefinitionStub;

public class GrEnumDefinitionElementType extends GrTypeDefinitionElementType<GrEnumTypeDefinition> {

  public GrEnumDefinitionElementType(String debugName) {
    super(debugName);
  }

  @Override
  public GrEnumTypeDefinition createPsi(@NotNull GrTypeDefinitionStub stub) {
    return new GrEnumTypeDefinitionImpl(stub);
  }

  @Override
  public @NotNull GrTypeDefinitionStub createStub(@NotNull GrEnumTypeDefinition psi, StubElement<?> parentStub) {
    return doCreateStub(this, psi, parentStub);
  }
}
