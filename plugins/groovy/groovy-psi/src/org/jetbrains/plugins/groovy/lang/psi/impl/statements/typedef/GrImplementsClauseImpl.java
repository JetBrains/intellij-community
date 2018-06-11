// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrImplementsClause;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrReferenceListStub;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 26.03.2007
 */
public class GrImplementsClauseImpl extends GrReferenceListImpl implements GrImplementsClause {

  public GrImplementsClauseImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  protected IElementType getKeywordType() {
    return GroovyTokenTypes.kIMPLEMENTS;
  }

  public GrImplementsClauseImpl(final GrReferenceListStub stub) {
    super(stub, GroovyElementTypes.IMPLEMENTS_CLAUSE);
  }

  @Override
  public void accept(@NotNull GroovyElementVisitor visitor) {
    visitor.visitImplementsClause(this);
  }

  public String toString() {
    return "Implements clause";
  }

  @NotNull
  @Override
  public PsiJavaCodeReferenceElement[] getReferenceElements() {
    return PsiJavaCodeReferenceElement.EMPTY_ARRAY;
  }

  @Override
  public Role getRole() {
    return Role.IMPLEMENTS_LIST;
  }
}
