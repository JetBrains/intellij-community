// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.stubs.elements;

import com.intellij.psi.stubs.StubElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrRecordDefinition;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.GrRecordDefinitionImpl;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrTypeDefinitionStub;

public class GrRecordDefinitionElementType extends GrTypeDefinitionElementType<GrRecordDefinition> {

  public GrRecordDefinitionElementType(String debugName) {
    super(debugName);
  }

  @Override
  public GrRecordDefinition createPsi(@NotNull GrTypeDefinitionStub stub) {
    return new GrRecordDefinitionImpl(stub);
  }

  @Override
  public @NotNull GrTypeDefinitionStub createStub(@NotNull GrRecordDefinition psi, StubElement<?> parentStub) {
    return doCreateStub(this, psi, parentStub);
  }
}
