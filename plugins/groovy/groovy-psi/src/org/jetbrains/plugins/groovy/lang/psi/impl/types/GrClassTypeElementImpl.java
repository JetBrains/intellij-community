// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl.types;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyStubElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrClassTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrClassReferenceType;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 26.04.2007
 */
public class GrClassTypeElementImpl extends GroovyPsiElementImpl implements GrClassTypeElement {
  public GrClassTypeElementImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(@NotNull GroovyElementVisitor visitor) {
    visitor.visitClassTypeElement(this);
  }

  @Override
  public String toString() {
    return "Type element";
  }

  @Override
  @NotNull
  public GrCodeReferenceElement getReferenceElement() {
    return findNotNullChildByType(GroovyElementTypes.REFERENCE_ELEMENT);
  }

  @Override
  @NotNull
  public PsiType getType() {
    return new GrClassReferenceType(getReferenceElement());
  }

  @Override
  public GrAnnotation @NotNull [] getAnnotations() {
    return findChildrenByType(GroovyStubElementTypes.ANNOTATION, GrAnnotation.class);
  }
}
