package org.jetbrains.plugins.groovy.lang.psi.impl.types;

import com.intellij.lang.ASTNode;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrClassTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrClassReferenceType;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 26.04.2007
 */
public class GrClassTypeElementImpl extends GroovyPsiElementImpl implements GrClassTypeElement {
  public GrClassTypeElementImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(GroovyElementVisitor visitor) {
    visitor.visitClassTypeElement(this);
  }

  public String toString() {
    return "Type element";
  }

  @NotNull
  public GrCodeReferenceElement getReferenceElement() {
    return findChildByClass(GrCodeReferenceElement.class);
  }

  @NotNull
  public PsiType getType() {
    return new GrClassReferenceType(getReferenceElement());
  }

  public PsiReference getReference() {
    return getReferenceElement();
  }
}
