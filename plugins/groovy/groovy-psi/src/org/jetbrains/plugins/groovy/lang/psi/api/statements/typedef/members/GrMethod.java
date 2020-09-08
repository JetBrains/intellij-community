// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import com.intellij.util.ArrayFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.extensions.NamedArgumentDescriptor;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocCommentOwner;
import org.jetbrains.plugins.groovy.lang.psi.GrNamedElement;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrParameterListOwner;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
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
                                  GrParameterListOwner, GrTopStatement, GrTypeParameterListOwner, GrDocCommentOwner {
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
  @NlsSafe @NotNull String getName();

  @Override
  @NotNull
  GrParameterList getParameterList();

  @Override
  @NotNull
  GrModifierList getModifierList();

  @NotNull
  Map<String, NamedArgumentDescriptor> getNamedParameters();

  GrReflectedMethod @NotNull [] getReflectedMethods();

  @Override
  GrParameter @NotNull [] getParameters();
}
