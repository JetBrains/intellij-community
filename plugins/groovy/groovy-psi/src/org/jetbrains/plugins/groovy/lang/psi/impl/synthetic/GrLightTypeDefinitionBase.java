// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.impl.synthetic;

import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.impl.InheritanceImplUtil;
import com.intellij.psi.impl.PsiClassImplUtil;
import com.intellij.psi.impl.PsiSuperMethodImplUtil;
import com.intellij.psi.impl.light.LightElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocComment;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrClassInitializer;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrExtendsClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrImplementsClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinitionBody;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMembersDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeParameter;
import org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.GrLightIdentifier;
import org.jetbrains.plugins.groovy.lang.psi.util.GrClassImplUtil;

import java.util.Collection;
import java.util.List;

public abstract class GrLightTypeDefinitionBase extends LightElement implements GrTypeDefinition {

  private final @NotNull GrLightModifierList myModifierList;
  private final @NotNull GrLightTypeParameterList myTypeParameterList;

  private @Nullable PsiClass myContainingClass;

  protected GrLightTypeDefinitionBase(@NotNull PsiElement context) {
    super(context.getManager(), context.getLanguage());
    myModifierList = new GrLightModifierList(this);
    myTypeParameterList = new GrLightTypeParameterList(context);
  }

  @Override
  public @NotNull PsiElement getNameIdentifierGroovy() {
    return new GrLightIdentifier(getManager(), getName());
  }

  @Override
  public @Nullable GrDocComment getDocComment() {
    return null;
  }

  @Override
  public void accept(@NotNull GroovyElementVisitor visitor) {
  }

  @Override
  public void acceptChildren(@NotNull GroovyElementVisitor visitor) {
  }

  @Override
  public boolean hasTypeParameters() {
    return getTypeParameters().length != 0;
  }

  @Override
  public @NotNull GrLightModifierList getModifierList() {
    return myModifierList;
  }

  @Override
  public boolean isDeprecated() {
    return false;
  }

  @Override
  public boolean hasModifierProperty(@PsiModifier.ModifierConstant @NonNls @NotNull String name) {
    return getModifierList().hasModifierProperty(name);
  }

  @Override
  public GrTypeParameter @NotNull [] getTypeParameters() {
    return getTypeParameterList().getTypeParameters();
  }

  @Override
  public boolean isInterface() {
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
  public PsiClass @NotNull [] getSupers(boolean includeSynthetic) {
    return GrClassImplUtil.getSupers(this, includeSynthetic);
  }

  @Override
  public @Nullable PsiReferenceList getExtendsList() {
    return null;
  }

  @Override
  public PsiClassType @NotNull [] getSuperTypes(boolean includeSynthetic) {
    return GrClassImplUtil.getSuperTypes(this, includeSynthetic);
  }

  @Override
  public @Nullable PsiReferenceList getImplementsList() {
    return null;
  }

  @Override
  public boolean isTrait() {
    return false;
  }

  @Override
  public @Nullable GrTypeDefinitionBody getBody() {
    return null;
  }

  @Override
  public String toString() {
    return "Light Groovy type definition: " + getName();
  }

  @Override
  public GrClassInitializer @NotNull [] getInitializers() {
    return GrClassInitializer.EMPTY_ARRAY;
  }

  @Override
  public GrMembersDeclaration @NotNull [] getMemberDeclarations() {
    return GrMembersDeclaration.EMPTY_ARRAY;
  }

  @Override
  public @Nullable String getQualifiedName() {
    return null;
  }

  @Override
  public @Nullable GrExtendsClause getExtendsClause() {
    return null;
  }

  @Override
  public @Nullable GrImplementsClause getImplementsClause() {
    return null;
  }

  @Override
  public @Nullable PsiField findCodeFieldByName(String name, boolean checkBases) {
    return GrClassImplUtil.findFieldByName(this, name, checkBases, false);
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
  public @Nullable PsiClass getSuperClass() {
    return GrClassImplUtil.getSuperClass(this);
  }

  @Override
  public boolean isAnonymous() {
    return false;
  }

  @Override
  public @NotNull GrLightTypeParameterList getTypeParameterList() {
    return myTypeParameterList;
  }

  @Override
  public PsiClass @NotNull [] getInterfaces() {
    return GrClassImplUtil.getInterfaces(this);
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
    return PsiClassImplUtil.getAllInnerClasses(this);
  }

  @Override
  public @Nullable PsiField findFieldByName(@NonNls String name, boolean checkBases) {
    return GrClassImplUtil.findFieldByName(this, name, checkBases, true);
  }

  @Override
  public @Nullable PsiMethod findMethodBySignature(@NotNull PsiMethod patternMethod, boolean checkBases) {
    return GrClassImplUtil.findMethodBySignature(this, patternMethod, checkBases);
  }

  @Override
  public PsiMethod @NotNull [] findMethodsBySignature(@NotNull PsiMethod patternMethod, boolean checkBases) {
    return GrClassImplUtil.findMethodsBySignature(this, patternMethod, checkBases);
  }

  @Override
  public PsiMethod @NotNull [] findMethodsByName(@NonNls String name, boolean checkBases) {
    return GrClassImplUtil.findMethodsByName(this, name, checkBases);
  }

  @Override
  public @NotNull List<Pair<PsiMethod, PsiSubstitutor>> findMethodsAndTheirSubstitutorsByName(@NonNls @NotNull String name, boolean checkBases) {
    return GrClassImplUtil.findMethodsAndTheirSubstitutorsByName(this, name, checkBases);
  }

  @Override
  public @NotNull List<Pair<PsiMethod, PsiSubstitutor>> getAllMethodsAndTheirSubstitutors() {
    return GrClassImplUtil.getAllMethodsAndTheirSubstitutors(this);
  }

  @Override
  public @Nullable PsiClass findInnerClassByName(@NonNls String name, boolean checkBases) {
    return GrClassImplUtil.findInnerClassByName(this, name, checkBases);
  }

  @Override
  public @Nullable PsiElement getLBrace() {
    return null;
  }

  @Override
  public @Nullable PsiElement getRBrace() {
    return null;
  }

  @Override
  public @Nullable PsiIdentifier getNameIdentifier() {
    return null;
  }

  @Override
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
  public @Nullable PsiClass getContainingClass() {
    return myContainingClass;
  }

  @Override
  public @NotNull Collection<HierarchicalMethodSignature> getVisibleSignatures() {
    return PsiSuperMethodImplUtil.getVisibleSignatures(this);
  }

  @Override
  public PsiElement setName(@NonNls @NotNull String name) {
    throw new UnsupportedOperationException();
  }

  public @NotNull GrLightTypeDefinitionBase setContainingClass(@Nullable PsiClass containingClass) {
    myContainingClass = containingClass;
    return this;
  }
}
