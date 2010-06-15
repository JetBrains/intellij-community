/*
 * Copyright (c) 2007, Your Corporation. All Rights Reserved.
 */

package org.jetbrains.plugins.groovy.lang.psi.api.statements;

import com.intellij.psi.PsiType;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMembersDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;


/**
 * @author: Dmitry.Krasilschikov
 * @date: 27.03.2007
 */
public interface GrVariableDeclaration extends GrStatement, GrMembersDeclaration {
  @Nullable
  GrTypeElement getTypeElementGroovy();

  GrVariable[] getVariables();

  void setType(@Nullable PsiType type);
}
