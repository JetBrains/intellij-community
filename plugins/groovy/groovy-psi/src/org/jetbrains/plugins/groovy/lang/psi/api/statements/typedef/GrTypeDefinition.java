/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrClassInitializer;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrTopLevelDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMembersDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeParameterList;

public interface GrTypeDefinition extends PsiClass, GrTopLevelDefinition, GrDocCommentOwner, GrMember {

  GrTypeDefinition[] EMPTY_ARRAY = new GrTypeDefinition[0];
  ArrayFactory<GrTypeDefinition> ARRAY_FACTORY = GrTypeDefinition[]::new;

  @NotNull
  @Override
  default PsiClassType[] getImplementsListTypes() {
    return getImplementsListTypes(true);
  }

  @NotNull
  @Override
  default PsiClassType[] getExtendsListTypes() {
    return getExtendsListTypes(true);
  }

  @NotNull
  @Override
  default PsiClass[] getSupers() {
    return getSupers(true);
  }

  @NotNull
  @Override
  default PsiClassType[] getSuperTypes() {
    return getSuperTypes(true);
  }

  @NotNull
  PsiClassType[] getImplementsListTypes(boolean includeSynthetic);

  @NotNull
  PsiClassType[] getExtendsListTypes(boolean includeSynthetic);

  @NotNull
  PsiClass[] getSupers(boolean includeSynthetic);

  @NotNull
  PsiClassType[] getSuperTypes(boolean includeSynthetic);

  @NotNull
  default GrTypeDefinition[] getCodeInnerClasses() {
    return EMPTY_ARRAY;
  }

  boolean isTrait();

  @Nullable
  GrTypeDefinitionBody getBody();

  @Override
  @NotNull
  GrField[] getFields();

  @NotNull
  GrField[] getCodeFields();

  @NotNull
  GrMethod[] getCodeConstructors();

  @Nullable
  PsiField findCodeFieldByName(String name, boolean checkBases);

  @Override
  @NotNull
  GrClassInitializer[] getInitializers();

  @NotNull
  GrMembersDeclaration[] getMemberDeclarations();

  @Override
  @Nullable
  String getQualifiedName();

  @Nullable
  GrExtendsClause getExtendsClause();

  @Nullable
  GrImplementsClause getImplementsClause();

  @NotNull
  GrMethod[] getCodeMethods();

  @NotNull
  PsiMethod[] findCodeMethodsByName(@NonNls String name, boolean checkBases);

  @NotNull
  PsiMethod[] findCodeMethodsBySignature(PsiMethod patternMethod, boolean checkBases);

  boolean isAnonymous();

  @Override
  @Nullable
  String getName();

  @Override
  GrTypeParameterList getTypeParameterList();
}
