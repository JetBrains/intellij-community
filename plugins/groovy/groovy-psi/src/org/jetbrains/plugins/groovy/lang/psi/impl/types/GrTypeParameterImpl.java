// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl.types;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.impl.InheritanceImplUtil;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocComment;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyStubElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
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
import org.jetbrains.plugins.groovy.lang.psi.impl.GrStubElementBase;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrTypeParameterStub;
import org.jetbrains.plugins.groovy.lang.psi.util.GrClassImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class GrTypeParameterImpl extends GrStubElementBase<GrTypeParameterStub> implements GrTypeParameter, StubBasedPsiElement<GrTypeParameterStub> {

  public GrTypeParameterImpl(@NotNull ASTNode node) {
    super(node);
  }

  public GrTypeParameterImpl(GrTypeParameterStub stub) {
    super(stub, GroovyStubElementTypes.TYPE_PARAMETER);
  }

  @Override
  @Nullable
  public GrTypeDefinitionBody getBody() {
    return null;
  }

  @Override
  public GrMembersDeclaration @NotNull [] getMemberDeclarations() {
    return GrMembersDeclaration.EMPTY_ARRAY;
  }

  @Override
  public GrExtendsClause getExtendsClause() {
    return null;
  }

  @Override
  public GrImplementsClause getImplementsClause() {
    return null;
  }

  @Override
  public GrMethod @NotNull [] getCodeMethods() {
    return GrMethod.EMPTY_ARRAY;
  }

  @Override
  public PsiMethod @NotNull [] findCodeMethodsByName(@NonNls String name, boolean checkBases) {
    return GrClassImplUtil.findCodeMethodsByName(this, name, checkBases);
  }

  @Override
  public PsiMethod @NotNull [] findCodeMethodsBySignature(PsiMethod patternMethod, boolean checkBases) {
    return GrClassImplUtil.findCodeMethodsBySignature(this, patternMethod, checkBases);
  }

  @Override
  public String toString() {
    return "Type parameter";
  }

  @Override
  @Nullable
  @NonNls
  public String getQualifiedName() {
    return null;
  }

  @Override
  public boolean isInterface() {
    return false;
  }

  @Override
  public boolean isAnonymous() {
    return false;
  }

  @Override
  public boolean isAnnotationType() {
    return false;
  }

  @Override
  public boolean isEnum() {
    return false;
  }

  @Override
  public boolean isTrait() {
    return false;
  }

  @Override
  @NotNull
  public PsiReferenceList getExtendsList() {
    return getRequiredStubOrPsiChild(GroovyStubElementTypes.TYPE_PARAMETER_BOUNDS_LIST);
  }

  @Override
  @Nullable
  public PsiReferenceList getImplementsList() {
    return null;
  }

  @Override
  public PsiClassType @NotNull [] getExtendsListTypes(boolean includeSynthetic) {
    return getExtendsList().getReferencedTypes();
  }

  @Override
  public PsiClassType @NotNull [] getImplementsListTypes(boolean includeSynthetic) {
    return PsiClassType.EMPTY_ARRAY;
  }

  @Override
  @Nullable
  public PsiClass getSuperClass() {
    return GrClassImplUtil.getSuperClass(this);
  }

  @Override
  public PsiClass @NotNull [] getInterfaces() {
    return PsiClass.EMPTY_ARRAY;
  }

  @Override
  public PsiClass @NotNull [] getSupers(boolean includeSynthetic) {
    return GrClassImplUtil.getSupers(this, includeSynthetic);
  }

  @Override
  public PsiClassType @NotNull [] getSuperTypes(boolean includeSynthetic) {
    return GrClassImplUtil.getSuperTypes(this, includeSynthetic);
  }

  @Override
  public GrField @NotNull [] getFields() {
    return GrField.EMPTY_ARRAY;
  }

  @Override
  public GrField @NotNull [] getCodeFields() {
    return GrField.EMPTY_ARRAY;
  }

  @Override
  public GrMethod @NotNull [] getCodeConstructors() {
    return GrMethod.EMPTY_ARRAY;
  }

  @Override
  public PsiField findCodeFieldByName(String name, boolean checkBases) {
    return null;
  }

  @Override
  public PsiMethod @NotNull [] getMethods() {
    return PsiMethod.EMPTY_ARRAY;
  }

  @Override
  public PsiMethod @NotNull [] getConstructors() {
    return PsiMethod.EMPTY_ARRAY;
  }

  @Override
  public PsiClass @NotNull [] getInnerClasses() {
    return PsiClass.EMPTY_ARRAY;
  }

  @Override
  public GrClassInitializer @NotNull [] getInitializers() {
    return GrClassInitializer.EMPTY_ARRAY;
  }

  @Override
  public PsiField @NotNull [] getAllFields() {
    return GrClassImplUtil.getAllFields(this);
  }

  @Override
  public PsiMethod @NotNull [] getAllMethods() {
    return GrClassImplUtil.getAllMethods(this);
  }

  @Override
  public PsiClass @NotNull [] getAllInnerClasses() {
    return PsiClass.EMPTY_ARRAY;
  }

  @Override
  @Nullable
  public PsiField findFieldByName(@NonNls String name, boolean checkBases) {
    return GrClassImplUtil.findFieldByName(this, name, checkBases, true);
  }

  @Override
  @Nullable
  public PsiMethod findMethodBySignature(@NotNull PsiMethod patternMethod, boolean checkBases) {
    return GrClassImplUtil.findMethodBySignature(this, patternMethod, checkBases);
  }

  @Override
  public PsiMethod @NotNull [] findMethodsBySignature(@NotNull PsiMethod patternMethod, boolean checkBases) {
    return GrClassImplUtil.findCodeMethodsBySignature(this, patternMethod, checkBases);
  }

  @Override
  public PsiMethod @NotNull [] findMethodsByName(@NonNls String name, boolean checkBases) {
    return GrClassImplUtil.findCodeMethodsByName(this, name, checkBases);
  }

  @Override
  @NotNull
  public List<Pair<PsiMethod, PsiSubstitutor>> findMethodsAndTheirSubstitutorsByName(@NonNls @NotNull String name, boolean checkBases) {
    return GrClassImplUtil.findMethodsAndTheirSubstitutorsByName(this, name, checkBases);
  }

  @Override
  @NotNull
  public List<Pair<PsiMethod, PsiSubstitutor>> getAllMethodsAndTheirSubstitutors() {
    return GrClassImplUtil.getAllMethodsAndTheirSubstitutors(this);
  }

  @Override
  @Nullable
  public PsiClass findInnerClassByName(@NonNls String name, boolean checkBases) {
    return null;
  }

  @Override
  @Nullable
  public PsiJavaToken getLBrace() {
    return null;
  }

  @Override
  @Nullable
  public PsiJavaToken getRBrace() {
    return null;
  }

  @Override
  @Nullable
  public PsiIdentifier getNameIdentifier() {
    return PsiUtil.getJavaNameIdentifier(this);
  }

  @Override
  @Nullable
  public PsiElement getScope() {
    return null;
  }

  @Override
  public boolean isInheritor(@NotNull PsiClass baseClass, boolean checkDeep) {
    return InheritanceImplUtil.isInheritor(this, baseClass, checkDeep);
  }

  @Override
  public boolean isInheritorDeep(@NotNull PsiClass baseClass, @Nullable PsiClass classToByPass) {
    return InheritanceImplUtil.isInheritorDeep(this, baseClass, classToByPass);
  }

  @Override
  @Nullable
  public PsiClass getContainingClass() {
    return null;
  }

  @Override
  @NotNull
  public Collection<HierarchicalMethodSignature> getVisibleSignatures() {
    return Collections.emptyList(); //todo
  }

  @Override
  public PsiTypeParameterListOwner getOwner() {
    final PsiElement parent = getParent();
    if (parent == null) throw new PsiInvalidElementAccessException(this);
    final PsiElement parentParent = parent.getParent();
    if (!(parentParent instanceof PsiTypeParameterListOwner)) {
      return null;
    }
    return (PsiTypeParameterListOwner)parentParent;
  }

  @Override
  public int getIndex() {
    final GrTypeParameterList list = (GrTypeParameterList)getParent();
    return list.getTypeParameterIndex(this);
  }

  @Override
  public PsiElement setName(@NonNls @NotNull String name) throws IncorrectOperationException {
    PsiImplUtil.setName(name, getNameIdentifierGroovy());
    return this;
  }

  @Override
  @NotNull
  public PsiElement getNameIdentifierGroovy() {
    PsiElement result = findChildByType(GroovyTokenTypes.mIDENT);
    assert result != null;
    return result;
  }

  @Override
  @Nullable
  public GrModifierList getModifierList() {
    return null;
  }

  @Override
  public boolean hasModifierProperty(@NonNls @NotNull String name) {
    return false;
  }

  @Override
  @Nullable
  public GrDocComment getDocComment() {
    return null;
  }

  @Override
  public boolean isDeprecated() {
    return false;
  }

  @Override
  public boolean hasTypeParameters() {
    return false;
  }

  @Override
  @Nullable
  public GrTypeParameterList getTypeParameterList() {
    return null;
  }

  @Override
  public PsiTypeParameter @NotNull [] getTypeParameters() {
    return PsiTypeParameter.EMPTY_ARRAY;
  }

  @Override
  public String getName() {
    final GrTypeParameterStub stub = getStub();
    if (stub != null) {
      return stub.getName();
    }
    return getNameIdentifierGroovy().getText();
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor, @NotNull ResolveState state, PsiElement lastParent, @NotNull PsiElement place) {
    return GrClassImplUtil.processDeclarations(this, processor, state, lastParent, place);
  }

  @Override
  public PsiAnnotation @NotNull [] getAnnotations() {
    return PsiAnnotation.EMPTY_ARRAY;
  }

  @Override
  public PsiAnnotation findAnnotation(@NotNull @NonNls String qualifiedName) {
    return null;
  }

  @Override
  @NotNull
  public PsiAnnotation addAnnotation(@NotNull @NonNls String qualifiedName) {
    throw new IncorrectOperationException();
  }
  @Override
  public PsiAnnotation @NotNull [] getApplicableAnnotations() {
    return PsiAnnotation.EMPTY_ARRAY;
  }

  @Override
  public void accept(@NotNull GroovyElementVisitor visitor) {
    visitor.visitTypeParameter(this);
  }
}
