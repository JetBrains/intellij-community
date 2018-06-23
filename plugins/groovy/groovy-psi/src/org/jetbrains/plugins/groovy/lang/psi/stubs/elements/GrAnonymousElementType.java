// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.stubs.elements;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrAnonymousClassDefinition;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.GrAnonymousClassDefinitionImpl;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrTypeDefinitionStub;

public class GrAnonymousElementType extends GrTypeDefinitionElementType<GrAnonymousClassDefinition> {

  public GrAnonymousElementType(String debugName) {
    super(debugName);
  }

  @Override
  public GrAnonymousClassDefinition createPsi(@NotNull GrTypeDefinitionStub stub) {
    return new GrAnonymousClassDefinitionImpl(stub);
  }
}
