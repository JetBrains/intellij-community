// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.lang.groovydoc.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GroovyDocPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.annotations.NotNull;

/**
 * @author ilyas
 */
public abstract class GroovyDocPsiElementImpl extends GroovyPsiElementImpl implements GroovyDocPsiElement {

  public GroovyDocPsiElementImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public PsiElement @NotNull [] getChildren() {
    return ((CompositeElement)getNode()).getChildrenAsPsiElements((TokenSet)null, PsiElement.ARRAY_FACTORY);
  }
}
