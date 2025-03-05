// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.plugins.groovy.lang.groovydoc.psi.impl;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocMethodParameter;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocReferenceElement;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocTagValueToken;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;

public class GrDocMethodParameterImpl extends GroovyDocPsiElementImpl implements GrDocMethodParameter {

  public GrDocMethodParameterImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public String toString() {
    return "GrDocMethodParameter";
  }

  @Override
  public void accept(@NotNull GroovyElementVisitor visitor) {
    visitor.visitDocMethodParameter(this);
  }

  @Override
  public @NotNull GrDocReferenceElement getTypeElement(){
    GrDocReferenceElement child = findChildByClass(GrDocReferenceElement.class);
    assert child != null;
    return child;
  }

  @Override
  public @Nullable GrDocTagValueToken getParameterElement(){
    return findChildByClass(GrDocTagValueToken.class);
  }
}
