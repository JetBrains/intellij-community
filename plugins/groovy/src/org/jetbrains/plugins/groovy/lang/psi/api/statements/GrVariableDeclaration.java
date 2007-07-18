/*
 * Copyright (c) 2007, Your Corporation. All Rights Reserved.
 */

package org.jetbrains.plugins.groovy.lang.psi.api.statements;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMembersDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import com.intellij.util.IncorrectOperationException;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 27.03.2007
 */
public interface GrVariableDeclaration extends GrStatement, GrMembersDeclaration {
  @Nullable
  GrTypeElement getTypeElementGroovy();

  GrModifierList getModifierList();

  GrVariable[] getVariables();

}
