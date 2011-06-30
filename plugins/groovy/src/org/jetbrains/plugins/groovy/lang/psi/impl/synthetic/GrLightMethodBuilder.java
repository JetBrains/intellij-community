/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.psi.impl.synthetic;

import com.intellij.navigation.ItemPresentation;
import com.intellij.psi.*;
import com.intellij.psi.impl.ElementPresentationUtil;
import com.intellij.psi.impl.PsiClassImplUtil;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.PsiSuperMethodImplUtil;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.psi.impl.light.LightEmptyImplementsList;
import com.intellij.psi.impl.light.LightIdentifier;
import com.intellij.psi.presentation.java.JavaPresentationUtil;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.ui.RowIcon;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocComment;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;

import javax.swing.*;
import java.util.List;

/**
 * @author Sergey Evdokimov
 */
public class GrLightMethodBuilder extends LightElement implements GrMethod {
  private final String myName;
  private PsiType myReturnType;
  private final GrModifierList myModifierList;
  private GrParameterList myParameterList;
  private Icon myBaseIcon;
  private PsiClass myContainingClass;
  private Object myMethodKind;
  private String[] namedParametersArray = ArrayUtil.EMPTY_STRING_ARRAY;

  public GrLightMethodBuilder(PsiManager manager, String name) {
    this(manager, name, new GrLightParameterListBuilder(manager, GroovyFileType.GROOVY_LANGUAGE), null);
  }

  public GrLightMethodBuilder(PsiManager manager,
                              String name,
                              GrParameterList parameterList,
                              GrModifierList modifierList) {
    super(manager, GroovyFileType.GROOVY_LANGUAGE);
    myName = name;
    myParameterList = parameterList;
    myModifierList = modifierList == null ? new GrLightModifierList(this, ArrayUtil.EMPTY_STRING_ARRAY) : modifierList;
  }

  public void setNamedParametersArray(@NotNull String[] namedParametersArray) {
    this.namedParametersArray = namedParametersArray;
  }

  @Override
  public ItemPresentation getPresentation() {
    return JavaPresentationUtil.getMethodPresentation(this);
  }

  public boolean hasTypeParameters() {
    return false;
  }

  @NotNull
  public PsiTypeParameter[] getTypeParameters() {
    return PsiTypeParameter.EMPTY_ARRAY;
  }

  public PsiTypeParameterList getTypeParameterList() {
    //todo
    return null;
  }

  public GrDocComment getDocComment() {
    //todo
    return null;
  }

  public boolean isDeprecated() {
    //todo
    return false;
  }

  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    throw new IncorrectOperationException("Please don't rename light methods");
  }

  @NotNull
  public String getName() {
    return myName;
  }

  @NotNull
  public HierarchicalMethodSignature getHierarchicalMethodSignature() {
    return PsiSuperMethodImplUtil.getHierarchicalMethodSignature(this);
  }

  public boolean hasModifierProperty(@NotNull String name) {
    return getModifierList().hasModifierProperty(name);
  }

  @Override
  public GrMember[] getMembers() {
    return GrMember.EMPTY_ARRAY;
  }

  @NotNull
  public GrModifierList getModifierList() {
    return myModifierList;
  }

  @NotNull
  @Override
  public String[] getNamedParametersArray() {
    return namedParametersArray;
  }

  public GrLightMethodBuilder addModifiers(String... modifiers) {
    for (String modifier : modifiers) {
      addModifier(modifier);
    }
    return this;
  }

  public GrLightMethodBuilder addModifier(String modifier) {
    ((GrLightModifierList)myModifierList).addModifier(modifier);
    return this;
  }

  public GrLightMethodBuilder setModifiers(String[] modifiers) {
    ((GrLightModifierList)myModifierList).clearModifiers();
    addModifiers(modifiers);
    return this;
  }

  @Override
  public GrOpenBlock getBlock() {
    return null;
  }

  @Override
  public void setBlock(GrCodeBlock newBlock) {
    throw new IncorrectOperationException();
  }

  @Override
  public GrTypeElement getReturnTypeElementGroovy() {
    return null;
  }

  @Override
  public PsiType getInferredReturnType() {
    return getReturnType();
  }

  public PsiType getReturnType() {
    return myReturnType;
  }

  public GrTypeElement setReturnType(PsiType returnType) {
    myReturnType = returnType;
    return null;
  }

  public PsiTypeElement getReturnTypeElement() {
    return null;
  }

  @Override
  public GrParameter[] getParameters() {
    return myParameterList.getParameters();
  }

  @NotNull
  public GrParameterList getParameterList() {
    return myParameterList;
  }

  public GrLightMethodBuilder addParameter(@NotNull GrParameter parameter) {
    ((GrLightParameterListBuilder)myParameterList).addParameter(parameter);
    return this;
  }

  public GrLightMethodBuilder addParameter(@NotNull String name, @NotNull String type, boolean isOptional) {
    return addParameter(name, JavaPsiFacade.getElementFactory(getProject()).createTypeFromText(type, this), isOptional);
  }

  public GrLightMethodBuilder addParameter(@NotNull String name, @NotNull PsiType type, boolean isOptional) {
    GrLightParameter param = new GrLightParameter(name, type, this).setOptional(isOptional);
    return addParameter(param);
  }

  @NotNull
  public PsiReferenceList getThrowsList() {
    //todo
    return new LightEmptyImplementsList(getManager());
  }

  public PsiCodeBlock getBody() {
    return null;
  }

  @Override
  public boolean isConstructor() {
    return false;
  }

  public boolean isVarArgs() {
    //todo
    return false;
  }

  @NotNull
  public MethodSignature getSignature(@NotNull PsiSubstitutor substitutor) {
    return MethodSignatureBackedByPsiMethod.create(this, substitutor);
  }

  public PsiIdentifier getNameIdentifier() {
    return null;
  }

  @NotNull
  public PsiMethod[] findSuperMethods() {
    return PsiSuperMethodImplUtil.findSuperMethods(this);
  }

  @NotNull
  public PsiMethod[] findSuperMethods(boolean checkAccess) {
    return PsiSuperMethodImplUtil.findSuperMethods(this, checkAccess);
  }

  @NotNull
  public PsiMethod[] findSuperMethods(PsiClass parentClass) {
    return PsiSuperMethodImplUtil.findSuperMethods(this, parentClass);
  }

  @NotNull
  public List<MethodSignatureBackedByPsiMethod> findSuperMethodSignaturesIncludingStatic(boolean checkAccess) {
    return PsiSuperMethodImplUtil.findSuperMethodSignaturesIncludingStatic(this, checkAccess);
  }

  public PsiMethod findDeepestSuperMethod() {
    return PsiSuperMethodImplUtil.findDeepestSuperMethod(this);
  }

  @NotNull
  public PsiMethod[] findDeepestSuperMethods() {
    return PsiSuperMethodImplUtil.findDeepestSuperMethods(this);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitMethod(this);
    }
  }

  public GrLightMethodBuilder setNavigationElement(PsiElement navigationElement) {
    super.setNavigationElement(navigationElement);
    return this;
  }

  public PsiClass getContainingClass() {
    return myContainingClass;
  }

  public GrLightMethodBuilder setContainingClass(PsiClass containingClass) {
    myContainingClass = containingClass;
    return this;
  }

  public GrLightMethodBuilder setMethodKind(@Nullable Object methodKind) {
    myMethodKind = methodKind;
    return this;
  }

  @NotNull
  @Override
  public PsiElement getNameIdentifierGroovy() {
    return new LightIdentifier(getManager(), getName());
  }

  @Override
  public void accept(GroovyElementVisitor visitor) {

  }

  @Override
  public void acceptChildren(GroovyElementVisitor visitor) {

  }

  public static boolean checkKind(@Nullable PsiElement method, @NotNull Object kind) {
    return method instanceof GrLightMethodBuilder && kind.equals(((GrLightMethodBuilder)method).myMethodKind);
  }

  public String toString() {
    return myMethodKind + ":" + getName();
  }

  @Override
  protected boolean isVisibilitySupported() {
    return true;
  }

  public Icon getElementIcon(final int flags) {
    Icon methodIcon = myBaseIcon != null ? myBaseIcon :
                      hasModifierProperty(PsiModifier.ABSTRACT) ? PlatformIcons.ABSTRACT_METHOD_ICON : PlatformIcons.METHOD_ICON;
    RowIcon baseIcon = createLayeredIcon(methodIcon, ElementPresentationUtil.getFlags(this, false));
    return ElementPresentationUtil.addVisibilityIcon(this, flags, baseIcon);
  }

  public GrLightMethodBuilder setBaseIcon(Icon baseIcon) {
    myBaseIcon = baseIcon;
    return this;
  }

  @Override
  public boolean isEquivalentTo(final PsiElement another) {
    return PsiClassImplUtil.isMethodEquivalentTo(this, another);
  }

  @NotNull
  public SearchScope getUseScope() {
    return PsiImplUtil.getMemberUseScope(this);
  }

  @Override
  @Nullable
  public PsiFile getContainingFile() {
    final PsiClass containingClass = getContainingClass();
    return containingClass == null ? null : containingClass.getContainingFile();
  }

  @Override
  public PsiElement getContext() {
    final PsiElement navElement = getNavigationElement();
    if (navElement != this) {
      return navElement;
    }

    final PsiClass cls = getContainingClass();
    if (cls != null) {
      return cls;
    }

    return getContainingFile();
  }

  public PsiMethodReceiver getMethodReceiver() {
    return null;
  }
  public PsiType getReturnTypeNoResolve() {
    return getReturnType();
  }

}
