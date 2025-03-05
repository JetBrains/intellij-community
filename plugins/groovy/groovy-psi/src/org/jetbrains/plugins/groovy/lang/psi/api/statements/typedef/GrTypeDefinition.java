// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.util.ArrayFactory;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocCommentOwner;
import org.jetbrains.plugins.groovy.lang.psi.GrNamedElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrClassInitializer;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMembersDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.GrTopStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeParameterList;

public interface GrTypeDefinition extends PsiClass, GrDocCommentOwner, GrMember, GrNamedElement, GrTopStatement {

  GrTypeDefinition[] EMPTY_ARRAY = new GrTypeDefinition[0];
  ArrayFactory<GrTypeDefinition> ARRAY_FACTORY = count -> count == 0 ? EMPTY_ARRAY : new GrTypeDefinition[count];

  @Override
  default PsiClassType @NotNull [] getImplementsListTypes() {
    return getImplementsListTypes(true);
  }

  @Override
  default PsiClassType @NotNull [] getExtendsListTypes() {
    return getExtendsListTypes(true);
  }

  @Override
  default PsiClass @NotNull [] getSupers() {
    return getSupers(true);
  }

  @Override
  default PsiClassType @NotNull [] getSuperTypes() {
    return getSuperTypes(true);
  }

  PsiClassType @NotNull [] getImplementsListTypes(boolean includeSynthetic);

  PsiClassType @NotNull [] getExtendsListTypes(boolean includeSynthetic);

  PsiClass @NotNull [] getSupers(boolean includeSynthetic);

  PsiClassType @NotNull [] getSuperTypes(boolean includeSynthetic);

  default GrTypeDefinition @NotNull [] getCodeInnerClasses() {
    return EMPTY_ARRAY;
  }

  boolean isTrait();

  @Nullable
  GrTypeDefinitionBody getBody();

  @Override
  GrField @NotNull [] getFields();

  GrField @NotNull [] getCodeFields();

  GrMethod @NotNull [] getCodeConstructors();

  @Nullable
  PsiField findCodeFieldByName(String name, boolean checkBases);

  @Override
  GrClassInitializer @NotNull [] getInitializers();

  GrMembersDeclaration @NotNull [] getMemberDeclarations();

  @Override
  @Nullable
  String getQualifiedName();

  @Nullable
  GrExtendsClause getExtendsClause();

  @Nullable
  GrImplementsClause getImplementsClause();

  default @Nullable GrPermitsClause getPermitsClause() {
    return null;
  }

  GrMethod @NotNull [] getCodeMethods();

  PsiMethod @NotNull [] findCodeMethodsByName(@NonNls String name, boolean checkBases);

  PsiMethod @NotNull [] findCodeMethodsBySignature(PsiMethod patternMethod, boolean checkBases);

  boolean isAnonymous();

  @Override
  @Nullable
  String getName();

  @Override
  GrTypeParameterList getTypeParameterList();
}
