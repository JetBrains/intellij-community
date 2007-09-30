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

package org.jetbrains.plugins.groovy.lang.psi.impl.types;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.Pair;
import com.intellij.pom.java.PomMemberOwner;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.*;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeParameterList;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;

import java.util.*;

/**
 * @author ilyas
 */
public class GrTypeParameterImpl extends GroovyPsiElementImpl implements GrTypeParameter {

  public GrTypeParameterImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString() {
    return "Type parameter";
  }

  @Nullable
  @NonNls
  public String getQualifiedName() {
    return null;
  }

  public boolean isInterface() {
    return false;
  }

  public boolean isAnnotationType() {
    return false;
  }

  public boolean isEnum() {
    return false;
  }

  @NotNull
  public PsiReferenceList getExtendsList() {
    final GrTypeParameterParameterExtendsListImpl list = findChildByClass(GrTypeParameterParameterExtendsListImpl.class);
    assert list != null;
    return list;
  }

  @Nullable
  public PsiReferenceList getImplementsList() {
    return null;
  }

  @NotNull
  public PsiClassType[] getExtendsListTypes() {
    return getExtendsList().getReferencedTypes();
  }

  @NotNull
  public PsiClassType[] getImplementsListTypes() {
    return new PsiClassType[0];
  }

  @Nullable
  public PsiClass getSuperClass() {
    return null;
  }

  public PsiClass[] getInterfaces() {
    return PsiClass.EMPTY_ARRAY;
  }

  @NotNull
  public PsiClass[] getSupers() {
    return PsiClass.EMPTY_ARRAY;
  }

  @NotNull
  public PsiClassType[] getSuperTypes() {
    final PsiClassType[] extendsTypes = getExtendsListTypes();
    if (extendsTypes.length > 0) return extendsTypes;
    return new PsiClassType[] {getManager().getElementFactory().createTypeByFQClassName("groovy.lang.GroovyObject", getResolveScope())};
  }

  @NotNull
  public PsiField[] getFields() {
    return PsiField.EMPTY_ARRAY;
  }

  @NotNull
  public PsiMethod[] getMethods() {
    return PsiMethod.EMPTY_ARRAY;
  }

  @NotNull
  public PsiMethod[] getConstructors() {
    return PsiMethod.EMPTY_ARRAY;
  }

  @NotNull
  public PsiClass[] getInnerClasses() {
    return PsiClass.EMPTY_ARRAY;
  }

  @NotNull
  public PsiClassInitializer[] getInitializers() {
    return PsiClassInitializer.EMPTY_ARRAY;
  }

  @NotNull
  public PsiField[] getAllFields() {
    return new PsiField[0];
  }

  @NotNull
  public PsiMethod[] getAllMethods() {
    return new PsiMethod[0];
  }

  @NotNull
  public PsiClass[] getAllInnerClasses() {
    return new PsiClass[0];
  }

  @Nullable
  public PsiField findFieldByName(@NonNls String name, boolean checkBases) {
    return null;
  }

  @Nullable
  public PsiMethod findMethodBySignature(PsiMethod patternMethod, boolean checkBases) {
    return null;
  }

  @NotNull
  public PsiMethod[] findMethodsBySignature(PsiMethod patternMethod, boolean checkBases) {
    return new PsiMethod[0];
  }

  @NotNull
  public PsiMethod[] findMethodsByName(@NonNls String name, boolean checkBases) {
    return new PsiMethod[0];
  }

  @NotNull
  public List<Pair<PsiMethod, PsiSubstitutor>> findMethodsAndTheirSubstitutorsByName(@NonNls String name, boolean checkBases) {
    return Collections.emptyList(); //todo
  }

  @NotNull
  public List<Pair<PsiMethod, PsiSubstitutor>> getAllMethodsAndTheirSubstitutors() {
    return Collections.emptyList(); //todo
  }

  @Nullable
  public PsiClass findInnerClassByName(@NonNls String name, boolean checkBases) {
    return null;
  }

  @Nullable
  public PsiJavaToken getLBrace() {
    return null;
  }

  @Nullable
  public PsiJavaToken getRBrace() {
    return null;
  }

  @Nullable
  public PsiIdentifier getNameIdentifier() {
    return null;
  }

  public PsiElement getScope() {
    return null;
  }

  public boolean isInheritor(@NotNull PsiClass baseClass, boolean checkDeep) {
    return false;
  }

  public boolean isInheritorDeep(PsiClass baseClass, @Nullable PsiClass classToByPass) {
    return false;
  }

  @Nullable
  public PomMemberOwner getPom() {
    return null;
  }

  @Nullable
  public PsiClass getContainingClass() {
    return null;
  }

  @NotNull
  public Collection<HierarchicalMethodSignature> getVisibleSignatures() {
    return Collections.emptyList(); //todo
  }

  @NotNull
  public PsiTypeParameterListOwner getOwner() {
    final PsiElement parent = getParent();
    if (parent == null) throw new PsiInvalidElementAccessException(this);
    final PsiElement parentParent = parent.getParent();
    return (PsiTypeParameterListOwner) parentParent;
  }

  public int getIndex() {
    final GrTypeParameterList list = (GrTypeParameterList) getParent();
    return list.getTypeParameterIndex(this);
  }

  public PsiElement setName(@NonNls @NotNull String name) throws IncorrectOperationException {
    PsiImplUtil.setName(name, getNameIdentifierGroovy());
    return this;
  }

  private PsiElement getNameIdentifierGroovy() {
    PsiElement result = findChildByType(GroovyElementTypes.mIDENT);
    assert result != null;
    return result;
  }

  @Nullable
  public PsiModifierList getModifierList() {
    return null;
  }

  public boolean hasModifierProperty(@NonNls @NotNull String name) {
    return false;
  }

  @Nullable
  public PsiDocComment getDocComment() {
    return null;
  }

  public boolean isDeprecated() {
    return false;
  }

  @Nullable
  public PsiMetaData getMetaData() {
    return null;
  }

  public boolean isMetaEnough() {
    return false;
  }

  public boolean hasTypeParameters() {
    return false;
  }

  @Nullable
  public PsiTypeParameterList getTypeParameterList() {
    return null;
  }

  @NotNull
  public PsiTypeParameter[] getTypeParameters() {
    return PsiTypeParameter.EMPTY_ARRAY;
  }
}
