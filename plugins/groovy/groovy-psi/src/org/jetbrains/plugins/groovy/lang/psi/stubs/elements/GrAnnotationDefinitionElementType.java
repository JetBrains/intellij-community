// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.stubs.elements;

import com.intellij.psi.stubs.StubElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrAnnotationTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.GrAnnotationTypeDefinitionImpl;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrTypeDefinitionStub;

public class GrAnnotationDefinitionElementType extends GrTypeDefinitionElementType<GrAnnotationTypeDefinition> {

  public GrAnnotationDefinitionElementType(String debugName) {
    super(debugName);
  }

  @Override
  public GrAnnotationTypeDefinition createPsi(@NotNull GrTypeDefinitionStub stub) {
    return new GrAnnotationTypeDefinitionImpl(stub);
  }

  @Override
  public @NotNull GrTypeDefinitionStub createStub(@NotNull GrAnnotationTypeDefinition psi, StubElement<?> parentStub) {
    return doCreateStub(this, psi, parentStub);
  }
}
