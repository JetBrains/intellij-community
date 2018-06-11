// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.lang.psi.impl.types;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrArrayTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;

/**
 * @author ilyas
 */
public class GrArrayTypeElementImpl extends GroovyPsiElementImpl implements GrArrayTypeElement {

  public GrArrayTypeElementImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(@NotNull GroovyElementVisitor visitor) {
    visitor.visitArrayTypeElement(this);
  }

  public String toString() {
    return "Array type";
  }

  @Override
  @NotNull
  public GrTypeElement getComponentTypeElement() {
    return findNotNullChildByClass(GrTypeElement.class);
  }

  @Override
  @NotNull
  public PsiType getType() {
    return getComponentTypeElement().getType().createArrayType();
  }
}
