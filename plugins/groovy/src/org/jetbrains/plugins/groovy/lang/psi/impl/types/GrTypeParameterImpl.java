/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.lang.psi.impl.types;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.impl.InheritanceImplUtil;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocComment;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrClassInitializer;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrExtendsClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrImplementsClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinitionBody;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMembersDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeParameterList;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrWildcardTypeArgument;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GrClassImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author ilyas
 */
public class GrTypeParameterImpl extends GroovyPsiElementImpl implements GrTypeParameter {
  public GrTypeDefinitionBody getBody() {
    return null;
  }

  @NotNull
  public GrClassInitializer[] getInitializersGroovy() {
    return GrClassInitializer.EMPTY_ARRAY;
  }

  @NotNull
  public GrMembersDeclaration[] getMemberDeclarations() {
    return GrMembersDeclaration.EMPTY_ARRAY;
  }

  public GrWildcardTypeArgument[] getTypeParametersGroovy() {
    return GrWildcardTypeArgument.EMPTY_ARRAY;
  }

  public GrExtendsClause getExtendsClause() {
    return null;
  }

  public GrImplementsClause getImplementsClause() {
    return null;
  }

  public String[] getSuperClassNames() {
    final PsiReference[] types = getExtendsList().getReferences();
    List<String> names = new ArrayList<String>(types.length);
    for (PsiReference type : types) {
      names.add(type.getCanonicalText());
    }
    return ArrayUtil.toStringArray(names);
  }

  @NotNull
  public GrMethod[] getGroovyMethods() {
    return GrMethod.EMPTY_ARRAY;
  }

  @NotNull
  public PsiMethod[] findCodeMethodsByName(@NonNls String name, boolean checkBases) {
    return GrClassImplUtil.findCodeMethodsByName(this, name, checkBases);
  }

  @NotNull
  public PsiMethod[] findCodeMethodsBySignature(PsiMethod patternMethod, boolean checkBases) {
    return GrClassImplUtil.findCodeMethodsBySignature(this, patternMethod, checkBases);
  }

  public PsiElement getLBraceGroovy() {
    return null;
  }

  public PsiElement getRBraceGroovy() {
    return null;
  }

  public <T extends GrMembersDeclaration> T addMemberDeclaration(T decl, PsiElement anchorBefore) throws IncorrectOperationException {
    throw new UnsupportedOperationException("Cannot add member declaration to GrTypeParameter");
  }

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

  public boolean isAnonymous() {
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
    return GrClassImplUtil.getSuperClass(this);
  }

  public PsiClass[] getInterfaces() {
    return PsiClass.EMPTY_ARRAY;
  }

  @NotNull
  public PsiClass[] getSupers() {
    return GrClassImplUtil.getSupers(this);
  }

  @NotNull
  public PsiClassType[] getSuperTypes() {
    return GrClassImplUtil.getSuperTypes(this);
  }

  @NotNull
  public GrField[] getFields() {
    return GrField.EMPTY_ARRAY;
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
    return GrClassImplUtil.getAllFields(this);
  }

  @NotNull
  public PsiMethod[] getAllMethods() {
    return GrClassImplUtil.getAllMethods(this);
  }

  @NotNull
  public PsiClass[] getAllInnerClasses() {
    return PsiClass.EMPTY_ARRAY;
  }

  @Nullable
  public PsiField findFieldByName(@NonNls String name, boolean checkBases) {
    return GrClassImplUtil.findFieldByName(this, name, checkBases);
  }

  @Nullable
  public PsiMethod findMethodBySignature(PsiMethod patternMethod, boolean checkBases) {
    return GrClassImplUtil.findMethodBySignature(this, patternMethod, checkBases);
  }

  @NotNull
  public PsiMethod[] findMethodsBySignature(PsiMethod patternMethod, boolean checkBases) {
    return GrClassImplUtil.findCodeMethodsBySignature(this, patternMethod, checkBases);
  }

  @NotNull
  public PsiMethod[] findMethodsByName(@NonNls String name, boolean checkBases) {
    return GrClassImplUtil.findCodeMethodsByName(this, name, checkBases);
  }

  @NotNull
  public List<Pair<PsiMethod, PsiSubstitutor>> findMethodsAndTheirSubstitutorsByName(@NonNls String name, boolean checkBases) {
    return GrClassImplUtil.findMethodsAndTheirSubstitutorsByName(this, name, checkBases);
  }

  @NotNull
  public List<Pair<PsiMethod, PsiSubstitutor>> getAllMethodsAndTheirSubstitutors() {
    return GrClassImplUtil.getAllMethodsAndTheirSubstitutors(this);
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
    return PsiUtil.getJavaNameIdentifier(this);
  }

  public PsiElement getScope() {
    return null;
  }

  public boolean isInheritor(@NotNull PsiClass baseClass, boolean checkDeep) {
    return InheritanceImplUtil.isInheritor(this, baseClass, checkDeep);
  }

  public boolean isInheritorDeep(PsiClass baseClass, @Nullable PsiClass classToByPass) {
    return InheritanceImplUtil.isInheritorDeep(this, baseClass, classToByPass);
  }

  @Nullable
  public PsiClass getContainingClass() {
    return null;
  }

  @NotNull
  public Collection<HierarchicalMethodSignature> getVisibleSignatures() {
    return Collections.emptyList(); //todo
  }

  public PsiTypeParameterListOwner getOwner() {
    final PsiElement parent = getParent();
    if (parent == null) throw new PsiInvalidElementAccessException(this);
    final PsiElement parentParent = parent.getParent();
    return (PsiTypeParameterListOwner)parentParent;
  }

  public int getIndex() {
    final GrTypeParameterList list = (GrTypeParameterList)getParent();
    return list.getTypeParameterIndex(this);
  }

  public PsiElement setName(@NonNls @NotNull String name) throws IncorrectOperationException {
    PsiImplUtil.setName(name, getNameIdentifierGroovy());
    return this;
  }

  @NotNull
  public PsiElement getNameIdentifierGroovy() {
    PsiElement result = findChildByType(GroovyElementTypes.mIDENT);
    assert result != null;
    return result;
  }

  @Nullable
  public GrModifierList getModifierList() {
    return null;
  }

  public boolean hasModifierProperty(@NonNls @NotNull String name) {
    return false;
  }

  @Nullable
  public GrDocComment getDocComment() {
    return null;
  }

  public boolean isDeprecated() {
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

  public String getName() {
    return getNameIdentifierGroovy().getText();
  }

  public boolean processDeclarations(@NotNull PsiScopeProcessor processor, @NotNull ResolveState state, PsiElement lastParent, @NotNull PsiElement place) {
    return GrClassImplUtil.processDeclarations(this, processor, state, lastParent, place);
  }

  @NotNull
  public PsiAnnotation[] getAnnotations() {
    return PsiAnnotation.EMPTY_ARRAY;
  }

  public PsiAnnotation findAnnotation(@NotNull @NonNls String qualifiedName) {
    return null;
  }

  @NotNull
  public PsiAnnotation addAnnotation(@NotNull @NonNls String qualifiedName) {
    throw new IncorrectOperationException();
  }
  @NotNull
  public PsiAnnotation[] getApplicableAnnotations() {
    return getAnnotations();
  }
  
}
