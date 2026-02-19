// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.parser;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.blocks.GrBlockImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.blocks.GrOpenBlockImpl;

public class GrConstructorBlockElementType extends GrCodeBlockElementType {

  public GrConstructorBlockElementType(String debugName) {
    super(debugName, false);
  }

  @Override
  public @NotNull GrBlockImpl createNode(CharSequence text) {
    return new GrOpenBlockImpl(this, text);
  }
}
