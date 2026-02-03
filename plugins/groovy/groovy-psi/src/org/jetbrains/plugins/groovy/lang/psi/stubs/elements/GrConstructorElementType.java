// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.stubs.elements;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.members.GrConstructorImpl;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrMethodStub;

public class GrConstructorElementType extends GrMethodElementType {

  public GrConstructorElementType(String debugName) {
    super(debugName);
  }

  @Override
  public GrMethod createPsi(@NotNull GrMethodStub stub) {
    return new GrConstructorImpl(stub);
  }
}
