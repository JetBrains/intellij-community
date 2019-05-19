// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.lang.psi;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

public interface GroovyPsiElement extends PsiElement {

  GroovyPsiElement[] EMPTY_ARRAY = new GroovyPsiElement[0];

  @Override
  @NotNull
  ASTNode getNode();

  void accept(@NotNull GroovyElementVisitor visitor);

  void acceptChildren(@NotNull GroovyElementVisitor visitor);
}
