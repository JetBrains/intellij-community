/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.impl.ElementPresentationUtil;
import com.intellij.psi.impl.PsiClassImplUtil;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.PsiSuperMethodImplUtil;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.psi.impl.light.LightIdentifier;
import com.intellij.psi.impl.light.LightReferenceListBuilder;
import com.intellij.psi.impl.light.LightTypeParameterListBuilder;
import com.intellij.psi.presentation.java.JavaPresentationUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.*;
import com.intellij.ui.RowIcon;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.extensions.NamedArgumentDescriptor;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocComment;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrReflectedMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;

import javax.swing.*;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author Sergey Evdokimov
 */
public class GrLightMethodBuilder extends LightElement implements GrMethod, OriginInfoAwareElement {

  public static final Key<String> KIND_KEY = Key.create("GrLightMethodBuilder.Key");

  protected String myName;
  private PsiType myReturnType = PsiType.VOID;
  private final GrLightModifierList myModifierList;
  private final GrLightParameterListBuilder myParameterList;
  private final LightTypeParameterListBuilder myTypeParameterList;
  private final LightReferenceListBuilder myThrowsList;
  private boolean myConstructor = false;
  private PsiClass myContainingClass;
  private Map<String, NamedArgumentDescriptor> myNamedParameters = Collections.emptyMap();
  
  private Icon myBaseIcon;
  private Object myMethodKind;
  private Object myData;
  private String myOriginInfo;

  public GrLightMethodBuilder(GrTypeDefinition constructedClass) {
    this(constructedClass.getManager(), constructedClass.getName());
    setConstructor(true);
    setContainingClass(constructedClass);
    setNavigationElement(constructedClass);
  }

  public GrLightMethodBuilder(PsiManager manager, String name) {
    super(manager, GroovyLanguage.INSTANCE);
    myName = name;
    myModifierList = new GrLightModifierList(this);
    myParameterList = new GrLightParameterListBuilder(manager, GroovyLanguage.INSTANCE);
    myTypeParameterList = new LightTypeParameterListBuilder(manager, GroovyLanguage.INSTANCE);
    myThrowsList = new LightReferenceListBuilder(manager, GroovyLanguage.INSTANCE, PsiReferenceList.Role.THROWS_LIST);
  }

  public GrLightMethodBuilder setNamedParameters(@NotNull Map<String, NamedArgumentDescriptor> namedParameters) {
    myNamedParameters = namedParameters;
    return this;
  }

  @Override
  public ItemPresentation getPresentation() {
    return JavaPresentationUtil.getMethodPresentation(this);
  }

  @Override
  public boolean hasTypeParameters() {
    return false;
  }

  @Override
  @NotNull
  public PsiTypeParameter[] getTypeParameters() {
    return getTypeParameterList().getTypeParameters();
  }

  @Override
  @NotNull
  public LightTypeParameterListBuilder getTypeParameterList() {
    return myTypeParameterList;
  }

  @Override
  public GrDocComment getDocComment() {
    return null;
  }

  @Override
  public boolean isDeprecated() {
    return false;
  }

  @Override
  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    throw new IncorrectOperationException("Please don't rename light methods");
  }

  @Override
  @NotNull
  public String getName() {
    return myName;
  }

  @Override
  @NotNull
  public HierarchicalMethodSignature getHierarchicalMethodSignature() {
    return PsiSuperMethodImplUtil.getHierarchicalMethodSignature(this);
  }

  @Override
  public boolean hasModifierProperty(@GrModifier.GrModifierConstant @NotNull String name) {
    return getModifierList().hasModifierProperty(name);
  }

  @Override
  public GrMember[] getMembers() {
    return GrMember.EMPTY_ARRAY;
  }

  @Override
  @NotNull
  public GrLightModifierList getModifierList() {
    return myModifierList;
  }

  @NotNull
  @Override
  public Map<String, NamedArgumentDescriptor> getNamedParameters() {
    return myNamedParameters;
  }

  @NotNull
  @Override
  public GrReflectedMethod[] getReflectedMethods() {
    return CachedValuesManager.getCachedValue(this, new CachedValueProvider<GrReflectedMethod[]>() {
      @Override
      public Result<GrReflectedMethod[]> compute() {
        return Result.create(GrReflectedMethodImpl.createReflectedMethods(GrLightMethodBuilder.this),
                             PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT);
      }
    });
  }

  public GrLightMethodBuilder addModifier(String modifier) {
    getModifierList().addModifier(modifier);
    return this;
  }

  public GrLightMethodBuilder addModifier(int modifier) {
    getModifierList().addModifier(modifier);
    return this;
  }

  public GrLightMethodBuilder setModifiers(String[] modifiers) {
    getModifierList().setModifiers(modifiers);
    return this;
  }

  public GrLightMethodBuilder setModifiers(int modifiers) {
    getModifierList().setModifiers(modifiers);
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
    PsiType returnType = getReturnType();
    if (returnType == null) {
      return null;
    }
    return new GrLightTypeElement(returnType, getManager());
  }

  @Override
  public PsiType getInferredReturnType() {
    return getReturnType();
  }

  @Override
  public PsiType getReturnType() {
    return myReturnType;
  }

  @Nullable
  public GrTypeElement setReturnType(String returnType, GlobalSearchScope scope) {
    setReturnType(JavaPsiFacade.getInstance(myManager.getProject()).getElementFactory().createTypeByFQClassName(returnType, scope));
    return null;
  }

  @Override
  public GrTypeElement setReturnType(@Nullable PsiType returnType) {
    myReturnType = returnType;
    return null;
  }

  @Override
  public PsiTypeElement getReturnTypeElement() {
    return null;
  }

  @Override
  public GrParameter[] getParameters() {
    return getParameterList().getParameters();
  }

  @Override
  @NotNull
  public GrLightParameterListBuilder getParameterList() {
    return myParameterList;
  }

  public GrLightMethodBuilder addParameter(@NotNull GrParameter parameter) {
    getParameterList().addParameter(parameter);
    return this;
  }

  public GrLightMethodBuilder addParameter(@NotNull String name, @NotNull String type, boolean isOptional) {
    return addParameter(name, JavaPsiFacade.getElementFactory(getProject()).createTypeFromText(type, this), isOptional);
  }

  public GrLightMethodBuilder addParameter(@NotNull String name, @NotNull PsiType type, boolean isOptional) {
    GrLightParameter param = new GrLightParameter(name, type, this).setOptional(isOptional);
    return addParameter(param);
  }

  @Override
  @NotNull
  public LightReferenceListBuilder getThrowsList() {
    return myThrowsList;
  }

  @Override
  public PsiCodeBlock getBody() {
    return null;
  }

  @Override
  public boolean isConstructor() {
    return myConstructor;
  }

  public GrLightMethodBuilder setConstructor(boolean constructor) {
    myConstructor = constructor;
    return this;
  }

  @Override
  public boolean isVarArgs() {
    GrParameter[] parameters = getParameterList().getParameters();
    if (parameters.length == 0) return false;
    return parameters[parameters.length - 1].isVarArgs();
  }

  @Override
  @NotNull
  public MethodSignature getSignature(@NotNull PsiSubstitutor substitutor) {
    return MethodSignatureBackedByPsiMethod.create(this, substitutor);
  }

  @Override
  public PsiIdentifier getNameIdentifier() {
    return null;
  }

  @Override
  @NotNull
  public PsiMethod[] findSuperMethods() {
    return PsiSuperMethodImplUtil.findSuperMethods(this);
  }

  @Override
  @NotNull
  public PsiMethod[] findSuperMethods(boolean checkAccess) {
    return PsiSuperMethodImplUtil.findSuperMethods(this, checkAccess);
  }

  @Override
  @NotNull
  public PsiMethod[] findSuperMethods(PsiClass parentClass) {
    return PsiSuperMethodImplUtil.findSuperMethods(this, parentClass);
  }

  @Override
  @NotNull
  public List<MethodSignatureBackedByPsiMethod> findSuperMethodSignaturesIncludingStatic(boolean checkAccess) {
    return PsiSuperMethodImplUtil.findSuperMethodSignaturesIncludingStatic(this, checkAccess);
  }

  @Override
  public PsiMethod findDeepestSuperMethod() {
    return PsiSuperMethodImplUtil.findDeepestSuperMethod(this);
  }

  @Override
  @NotNull
  public PsiMethod[] findDeepestSuperMethods() {
    return PsiSuperMethodImplUtil.findDeepestSuperMethods(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitMethod(this);
    }
  }

  @Override
  public PsiClass getContainingClass() {
    return myContainingClass;
  }

  public GrLightMethodBuilder setContainingClass(PsiClass containingClass) {
    myContainingClass = containingClass;
    return this;
  }

  public Object getMethodKind() {
    return myMethodKind;
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

  public static Object getMethodKind(@Nullable PsiElement method) {
    if (method == null) return null;

    if (method instanceof GrLightMethodBuilder) {
      return ((GrLightMethodBuilder)method).getMethodKind();
    }

    return method.getUserData(KIND_KEY);
  }

  public static boolean checkKind(@Nullable PsiElement method, @NotNull Object kind) {
    return kind.equals(getMethodKind(method));
  }

  public static boolean checkKind(@Nullable PsiElement method, @NotNull Object kind1, @NotNull Object kind2) {
    Object kind = getMethodKind(method);
    return kind1.equals(kind) || kind2.equals(kind);
  }

  @Override
  public String toString() {
    return (myMethodKind == null ? "" : myMethodKind + ":") + getName();
  }

  @Override
  protected boolean isVisibilitySupported() {
    return true;
  }

  @Override
  public Icon getElementIcon(final int flags) {
    Icon methodIcon = myBaseIcon != null ? myBaseIcon :
                      hasModifierProperty(PsiModifier.ABSTRACT) ? PlatformIcons.ABSTRACT_METHOD_ICON : PlatformIcons.METHOD_ICON;
    RowIcon baseIcon = ElementPresentationUtil.createLayeredIcon(methodIcon, this, false);
    return ElementPresentationUtil.addVisibilityIcon(this, flags, baseIcon);
  }

  public GrLightMethodBuilder setBaseIcon(Icon baseIcon) {
    myBaseIcon = baseIcon;
    return this;
  }

  @Override
  public boolean isEquivalentTo(final PsiElement another) {
    return PsiClassImplUtil.isMethodEquivalentTo(this, another) || getNavigationElement() == another;
  }

  @Override
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

  protected void copyData(GrLightMethodBuilder dst) {
    dst.setMethodKind(getMethodKind());
    dst.setData(getData());
    dst.setNamedParameters(getNamedParameters());
    if (getNavigationElement() != this) {
      dst.setNavigationElement(getNavigationElement());
    }
    dst.setBaseIcon(myBaseIcon);
    dst.setReturnType(getReturnType());
    dst.setContainingClass(getContainingClass());

    dst.getModifierList().copyModifiers(this);

    dst.getParameterList().clear();
    for (GrParameter parameter : getParameterList().getParameters()) {
      dst.addParameter(parameter);
    }
  }

  @Override
  public GrLightMethodBuilder copy() {
    GrLightMethodBuilder copy = new GrLightMethodBuilder(myManager, myName);
    copyData(copy);
    return copy;
  }

  public <T> T getData() {
    //noinspection unchecked
    return (T)myData;
  }

  @Nullable
  public static <T> T getData(@Nullable PsiElement method, @NotNull Object kind) {
    if (method instanceof GrLightMethodBuilder) {
      if (kind.equals(((GrLightMethodBuilder)method).getMethodKind())) {
        return ((GrLightMethodBuilder)method).<T>getData();
      }
    }
    return null;
  }

  public GrLightMethodBuilder setData(@Nullable Object data) {
    myData = data;
    return this;
  }

  public GrLightMethodBuilder addException(PsiClassType type) {
    getThrowsList().addReference(type);
    return this;
  }

  @Nullable
  @Override
  public String getOriginInfo() {
    return myOriginInfo;
  }

  public void setOriginInfo(@Nullable String originInfo) {
    myOriginInfo = originInfo;
  }
}
