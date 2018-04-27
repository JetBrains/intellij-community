// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.stubs.elements;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrThrowsClause;
import org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.GrThrowsClauseImpl;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrReferenceListStub;

public class GrThrowsClauseElementType extends GrReferenceListElementType<GrThrowsClause> {

  public GrThrowsClauseElementType(String debugName) {
    super(debugName);
  }

  @Override
  public GrThrowsClause createPsi(@NotNull GrReferenceListStub stub) {
    return new GrThrowsClauseImpl(stub);
  }
}
