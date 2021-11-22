// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.stubs.elements;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrPermitsClause;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.GrPermitsClauseImpl;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrReferenceListStub;

public class GrPermitsClauseElementType extends GrReferenceListElementType<GrPermitsClause> {

  public GrPermitsClauseElementType(String debugName) {
    super(debugName);
  }

  @Override
  public GrPermitsClause createPsi(@NotNull GrReferenceListStub stub) {
    return new GrPermitsClauseImpl(stub);
  }
}
