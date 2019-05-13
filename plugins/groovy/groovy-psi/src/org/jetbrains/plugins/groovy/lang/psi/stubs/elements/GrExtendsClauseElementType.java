// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.stubs.elements;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrExtendsClause;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.GrExtendsClauseImpl;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrReferenceListStub;

public class GrExtendsClauseElementType extends GrReferenceListElementType<GrExtendsClause> {

  public GrExtendsClauseElementType(String debugName) {
    super(debugName);
  }

  @Override
  public GrExtendsClause createPsi(@NotNull GrReferenceListStub stub) {
    return new GrExtendsClauseImpl(stub);
  }
}
