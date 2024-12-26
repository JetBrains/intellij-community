// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.impl.types;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiWildcardType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyStubElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrWildcardTypeArgument;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;

/**
 * @author Dmitry.Krasilschikov
 */
public class GrWildcardTypeArgumentImpl extends GroovyPsiElementImpl implements GrWildcardTypeArgument {
  private static final Logger LOG = Logger.getInstance(GrWildcardTypeArgumentImpl.class);

  public GrWildcardTypeArgumentImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(@NotNull GroovyElementVisitor visitor) {
    visitor.visitWildcardTypeArgument(this);
  }

  @Override
  public String toString() {
    return "Type argument";
  }

  @Override
  public @NotNull PsiType getType() {
    final GrTypeElement boundTypeElement = getBoundTypeElement();
    if (boundTypeElement == null) return PsiWildcardType.createUnbounded(getManager());
    if (isExtends()) return PsiWildcardType.createExtends(getManager(), boundTypeElement.getType());
    if (isSuper()) return PsiWildcardType.createSuper(getManager(), boundTypeElement.getType());

    LOG.error("Untested case");
    return null;
  }

  @Override
  public GrTypeElement getBoundTypeElement() {
    return findChildByClass(GrTypeElement.class);
  }

  private boolean isExtends() {
    return findChildByType(GroovyTokenTypes.kEXTENDS) != null;
  }

  private boolean isSuper() {
    return findChildByType(GroovyTokenTypes.kSUPER) != null;
  }

  @Override
  public GrAnnotation @NotNull [] getAnnotations() {
    return findChildrenByType(GroovyStubElementTypes.ANNOTATION, GrAnnotation.class);
  }
}
