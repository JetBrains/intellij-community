// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyStubElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrImplementsClause;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrReferenceListStub;

/**
 * @author Dmitry.Krasilschikov
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
    super(stub, GroovyStubElementTypes.IMPLEMENTS_CLAUSE);
  }

  @Override
  public void accept(@NotNull GroovyElementVisitor visitor) {
    visitor.visitImplementsClause(this);
  }

  @Override
  public String toString() {
    return "Implements clause";
  }

  @Override
  public PsiJavaCodeReferenceElement @NotNull [] getReferenceElements() {
    return PsiJavaCodeReferenceElement.EMPTY_ARRAY;
  }

  @Override
  public Role getRole() {
    return Role.IMPLEMENTS_LIST;
  }
}
