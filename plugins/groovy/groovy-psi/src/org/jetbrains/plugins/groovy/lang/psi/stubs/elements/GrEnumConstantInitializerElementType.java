// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.stubs.elements;

import com.intellij.psi.stubs.StubElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrEnumConstantInitializer;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.GrEnumConstantInitializerImpl;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrTypeDefinitionStub;

public class GrEnumConstantInitializerElementType extends GrTypeDefinitionElementType<GrEnumConstantInitializer> {

  public GrEnumConstantInitializerElementType(String debugName) {
    super(debugName);
  }

  @Override
  public GrEnumConstantInitializer createPsi(@NotNull GrTypeDefinitionStub stub) {
    return new GrEnumConstantInitializerImpl(stub);
  }

  @Override
  public @NotNull GrTypeDefinitionStub createStub(@NotNull GrEnumConstantInitializer psi, StubElement<?> parentStub) {
    return doCreateStub(this, psi, parentStub);
  }
}
