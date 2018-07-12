// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.blocks;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifiableCodeBlock;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrClassInitializer;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;

/**
 * @author ilyas
 */
public class GrOpenBlockImpl extends GrBlockImpl implements GrOpenBlock, PsiModifiableCodeBlock {

  public GrOpenBlockImpl(@NotNull IElementType type, CharSequence buffer) {
    super(type, buffer);
  }

  @Override
  public void accept(@NotNull GroovyElementVisitor visitor) {
    visitor.visitOpenBlock(this);
  }

  public String toString() {
    return "Open block";
  }

  @Override
  public boolean isTopControlFlowOwner() {
    final PsiElement parent = getParent();
    return parent instanceof GrMethod || parent instanceof GrClassInitializer;
  }

  @Override
  public boolean shouldChangeModificationCount(PsiElement place) {
    final PsiElement parent = getParent();
    return !(parent instanceof GrMethod) && !(parent instanceof GrClassInitializer);
  }
}
