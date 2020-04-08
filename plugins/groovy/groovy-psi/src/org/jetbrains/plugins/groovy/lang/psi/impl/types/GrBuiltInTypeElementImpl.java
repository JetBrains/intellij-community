// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.lang.psi.impl.types;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.parser.GroovyStubElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrBuiltInTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;

/**
 * @author ilyas
 */
public class GrBuiltInTypeElementImpl extends GroovyPsiElementImpl implements GrBuiltInTypeElement {

  public GrBuiltInTypeElementImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(@NotNull GroovyElementVisitor visitor) {
    visitor.visitBuiltinTypeElement(this);
  }

  @Override
  public String toString() {
    return "Built in type";
  }

  @Override
  @NotNull
  public PsiType getType() {
    return TypesUtil.getPrimitiveTypeByText(getText());
  }

  @Override
  public GrAnnotation @NotNull [] getAnnotations() {
    return findChildrenByType(GroovyStubElementTypes.ANNOTATION, GrAnnotation.class);
  }
}
