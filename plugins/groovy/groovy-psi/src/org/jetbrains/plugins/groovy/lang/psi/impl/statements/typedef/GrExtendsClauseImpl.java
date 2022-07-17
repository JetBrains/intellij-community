// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyStubElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrExtendsClause;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrReferenceListStub;

/**
 * @author Dmitry.Krasilschikov
 */
public class GrExtendsClauseImpl extends GrReferenceListImpl implements GrExtendsClause {

  public GrExtendsClauseImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  protected IElementType getKeywordType() {
    return GroovyTokenTypes.kEXTENDS;
  }

  public GrExtendsClauseImpl(final GrReferenceListStub stub) {
    super(stub, GroovyStubElementTypes.EXTENDS_CLAUSE);
  }

  @Override
  public void accept(@NotNull GroovyElementVisitor visitor) {
    visitor.visitExtendsClause(this);
  }

  @Override
  public String toString() {
    return "Extends clause";
  }

  @Override
  public PsiJavaCodeReferenceElement @NotNull [] getReferenceElements() {
    return PsiJavaCodeReferenceElement.EMPTY_ARRAY;
  }

  @Override
  public Role getRole() {
    return Role.EXTENDS_LIST;
  }
}
