// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.parser;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.blocks.GrBlockImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.blocks.GrClosableBlockImpl;

public class GrClosureElementType extends GrCodeBlockElementType {


  public GrClosureElementType(String debugName) {
    this(debugName, false);
  }

  public GrClosureElementType(String debugName, boolean isInsideSwitch) {
    super(debugName, isInsideSwitch);
  }

  @NotNull
  @Override
  public GrBlockImpl createNode(CharSequence text) {
    return new GrClosableBlockImpl(this, text);
  }
}
