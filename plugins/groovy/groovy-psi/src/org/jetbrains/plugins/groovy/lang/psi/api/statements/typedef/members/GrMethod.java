/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
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

import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import com.intellij.util.ArrayFactory;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.extensions.NamedArgumentDescriptor;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocCommentOwner;
import org.jetbrains.plugins.groovy.lang.psi.GrNamedElement;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrParametersOwner;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.GrTopStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeParameterListOwner;

import java.util.Map;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 26.03.2007
 */
public interface GrMethod extends GrMembersDeclaration, GrNamedElement, PsiMethod, GrMember,
                                  GrParametersOwner, GrTopStatement, GrTypeParameterListOwner, GrDocCommentOwner {
  GrMethod[] EMPTY_ARRAY = new GrMethod[0];
  ArrayFactory<GrMethod> ARRAY_FACTORY = count -> count == 0 ? EMPTY_ARRAY : new GrMethod[count];

  @Nullable
  GrOpenBlock getBlock();

  default boolean hasBlock() { return getBlock() != null; }

  void setBlock(GrCodeBlock newBlock);

  @Nullable
  GrTypeElement getReturnTypeElementGroovy();

  /**
   * @return The inferred return type, which may be much more precise then the getReturnType() result, but takes longer to calculate.
   * To be used only in the Groovy code insight
   */
  @Nullable
  PsiType getInferredReturnType();

  /**
   * @return the static return type, which will appear in the compiled Groovy class
   */
  @Override
  @Nullable
  PsiType getReturnType();

  @Nullable
  GrTypeElement setReturnType(@Nullable PsiType newReturnType);

  @Override
  @NotNull
  @NonNls
  String getName();

  @Override
  @NotNull
  GrParameterList getParameterList();

  @Override
  @NotNull
  GrModifierList getModifierList();

  @NotNull
  Map<String, NamedArgumentDescriptor> getNamedParameters();

  @NotNull
  GrReflectedMethod[] getReflectedMethods();
}
