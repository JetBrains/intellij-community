// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.stubs.elements;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrImplementsClause;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.GrImplementsClauseImpl;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrReferenceListStub;

public class GrImplementsClauseElementType extends GrReferenceListElementType<GrImplementsClause> {

  public GrImplementsClauseElementType(String debugName) {
    super(debugName);
  }

  @Override
  public GrImplementsClause createPsi(@NotNull GrReferenceListStub stub) {
    return new GrImplementsClauseImpl(stub);
  }
}
