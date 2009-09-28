package org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;

/**
 * @author ven
 */
public interface GrMembersDeclaration extends GroovyPsiElement {
  public static final GrMembersDeclaration[] EMPTY_ARRAY = new GrMembersDeclaration[0];

  GrMember[] getMembers();

  @NotNull
  GrModifierList getModifierList();
}
