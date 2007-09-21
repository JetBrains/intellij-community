/*
 * Copyright 2000-2007 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members;

import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierList;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GrNamedElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrParametersOwner;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrTopLevelDefintion;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeParameterListOwner;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 26.03.2007
 */
public interface GrMethod extends GrMember, GrMembersDeclaration, GrNamedElement, PsiMethod,
    GrParametersOwner, GrTopLevelDefintion, GrTypeParameterListOwner {
  public static final GrMethod[] EMPTY_ARRAY = new GrMethod[0];
  Key<Boolean> BUILDER_METHOD = Key.create("BUILDER_METHOD");

  @Nullable
  GrOpenBlock getBlock();

  @Nullable
  GrTypeElement getReturnTypeElementGroovy();

  PsiElement getNameIdentifierGroovy();

  @NotNull
  @NonNls
  String getName();

  @NotNull
  PsiModifierList getModifierList();
}
