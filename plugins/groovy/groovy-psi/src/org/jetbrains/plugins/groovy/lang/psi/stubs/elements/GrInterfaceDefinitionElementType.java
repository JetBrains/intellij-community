// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.stubs.elements;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrInterfaceDefinition;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.GrInterfaceDefinitionImpl;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrTypeDefinitionStub;

public class GrInterfaceDefinitionElementType extends GrTypeDefinitionElementType<GrInterfaceDefinition> {

  public GrInterfaceDefinitionElementType(String debugName) {
    super(debugName);
  }

  @Override
  public GrInterfaceDefinition createPsi(@NotNull GrTypeDefinitionStub stub) {
    return new GrInterfaceDefinitionImpl(stub);
  }
}
