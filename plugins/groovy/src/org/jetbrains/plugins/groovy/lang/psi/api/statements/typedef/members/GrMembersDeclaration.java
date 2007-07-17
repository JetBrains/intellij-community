package org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members;

import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;

/**
 * @author ven
 */
public interface GrMembersDeclaration extends GroovyPsiElement {
  public static final GrMembersDeclaration[] EMPTY_ARRAY = new GrMembersDeclaration[0];

  GrMember[] getMembers();
}
