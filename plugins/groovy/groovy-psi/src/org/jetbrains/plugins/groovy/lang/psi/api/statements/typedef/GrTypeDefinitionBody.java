/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrClassInitializer;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMembersDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrVariableDeclarationOwner;

/**
 * @autor: Dmitry.Krasilschikov
 * @date: 18.03.2007
 */
public interface GrTypeDefinitionBody extends GrVariableDeclarationOwner {

  @NotNull
  GrField[] getFields();

  @NotNull
  GrMethod[] getMethods();

  @NotNull
  GrMembersDeclaration[] getMemberDeclarations();

  @Nullable
  PsiElement getLBrace();

  @Nullable
  PsiElement getRBrace();

  @NotNull
  GrClassInitializer[] getInitializers();

  @NotNull
  GrTypeDefinition[] getInnerClasses();
}
