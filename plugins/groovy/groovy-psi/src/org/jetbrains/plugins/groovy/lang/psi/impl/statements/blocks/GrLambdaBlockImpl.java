// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.blocks;

import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;

/**
 * @author ilyas
 */
public class GrLambdaBlockImpl extends GrBlockImpl {

  public GrLambdaBlockImpl(@NotNull IElementType type, CharSequence buffer) {
    super(type, buffer);
  }

  @Override
  public void accept(@NotNull GroovyElementVisitor visitor) {
    visitor.visitLambdaBlock(this);
  }

  @Override
  public String toString() {
    return "Lambda body block";
  }

  @Override
  public boolean isTopControlFlowOwner() {
    return true;
  }
}
