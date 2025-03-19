// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.plugins.groovy.lang.groovydoc.psi.impl;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;

public class GrDocReferenceElementImpl extends GroovyDocPsiElementImpl implements GrDocReferenceElement {
  
  public GrDocReferenceElementImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public String toString() {
    return "GrDocReferenceElement";
  }

  @Override
  public @Nullable GrCodeReferenceElement getReferenceElement() {
    return findChildByClass(GrCodeReferenceElement.class);
  }
}
