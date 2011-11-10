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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.ElementPresentationUtil;
import com.intellij.psi.impl.PsiClassImplUtil;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.PsiSuperMethodImplUtil;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.psi.impl.light.LightIdentifier;
import com.intellij.psi.impl.light.LightReferenceListBuilder;
import com.intellij.psi.presentation.java.JavaPresentationUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.*;
import com.intellij.ui.RowIcon;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.extensions.NamedArgumentDescriptor;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocComment;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
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
public class GrLightMethodBuilder extends LightElement implements GrMethod {
  protected String myName;
  private PsiType myReturnType = PsiType.VOID;
  private final GrLightModifierList myModifierList;
  private final GrLightParameterListBuilder myParameterList;
  private Icon myBaseIcon;
  private PsiClass myContainingClass;
  private Object myMethodKind;
  private Map<String, NamedArgumentDescriptor> myNamedParameters = Collections.emptyMap();
  private final PsiReferenceList myThrowsList;

  private Object myData;
  private boolean myConstructor;

  public GrLightMethodBuilder(PsiManager manager, String name) {
    super(manager, GroovyFileType.GROOVY_LANGUAGE);
    myName = name;
    myParameterList = new GrLightParameterListBuilder(manager, GroovyFileType.GROOVY_LANGUAGE);
    myModifierList = new GrLightModifierList(this);
    myConstructor = false;
    myThrowsList = new LightReferenceListBuilder(manager, GroovyFileType.GROOVY_LANGUAGE, PsiReferenceList.Role.THROWS_LIST);
  }

  public void setNamedParameters(@NotNull Map<String, NamedArgumentDescriptor> namedParameters) {
    this.myNamedParameters = namedParameters;
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
    return null;
  }

  public GrDocComment getDocComment() {
    return null;
  }

  public boolean isDeprecated() {
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
    return CachedValuesManager.getManager(getProject()).getCachedValue(this, new CachedValueProvider<GrReflectedMethod[]>() {
      @Override
      public Result<GrReflectedMethod[]> compute() {
        return Result.create(GrReflectedMethodImpl.createReflectedMethods(GrLightMethodBuilder.this), PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT);
      }
    });
  }

  public GrLightMethodBuilder addModifier(String modifier) {
    myModifierList.addModifier(modifier);
    return this;
  }

  public GrLightMethodBuilder addModifier(int modifier) {
    myModifierList.addModifier(modifier);
    return this;
  }

  public GrLightMethodBuilder setModifiers(String[] modifiers) {
    myModifierList.setModifiers(modifiers);
    return this;
  }

  public GrLightMethodBuilder setModifiers(int modifiers) {
    myModifierList.setModifiers(modifiers);
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

  @Nullable
  public GrTypeElement setReturnType(String returnType, GlobalSearchScope scope) {
    setReturnType(JavaPsiFacade.getInstance(myManager.getProject()).getElementFactory().createTypeByFQClassName(returnType, scope));
    return null;
  }

  public GrTypeElement setReturnType(@Nullable PsiType returnType) {
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
  public GrLightParameterListBuilder getParameterList() {
    return myParameterList;
  }

  public GrLightMethodBuilder addParameter(@NotNull GrParameter parameter) {
    myParameterList.addParameter(parameter);
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
    return myThrowsList;
  }

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

  public boolean isVarArgs() {
    GrParameter[] parameters = myParameterList.getParameters();
    if (parameters.length == 0) return false;
    return parameters[parameters.length - 1].isVarArgs();
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

  protected void copyData(GrLightMethodBuilder dest) {
    dest.setMethodKind(myMethodKind);
    dest.setData(myData);
    dest.setNamedParameters(myNamedParameters);
    if (getNavigationElement() != this) {
      dest.setNavigationElement(getNavigationElement());
    }
    dest.setBaseIcon(myBaseIcon);
    dest.setReturnType(myReturnType);
    dest.setContainingClass(myContainingClass);
    
    dest.getModifierList().copyModifiers(this);

    dest.getParameterList().clear();
    for (GrParameter parameter : myParameterList.getParameters()) {
      dest.addParameter(parameter);
    }
  }

  @Override
  public GrLightMethodBuilder copy() {
    GrLightMethodBuilder copy = new GrLightMethodBuilder(myManager, myName);
    copyData(copy);
    return copy;
  }

  public static GrLightMethodBuilder wrap(PsiMethod method) {
    GrLightMethodBuilder res = new GrLightMethodBuilder(method.getManager(), method.getName());

    res.setReturnType(method.getReturnType());
    res.setNavigationElement(method.getNavigationElement());

    res.setContainingClass(method.getContainingClass());

    res.getModifierList().copyModifiers(method);

    for (PsiParameter parameter : method.getParameterList().getParameters()) {
      GrLightParameter p = new GrLightParameter(StringUtil.notNullize(parameter.getName()), parameter.getType(), res);

      if (parameter instanceof GrParameter) {
        p.setOptional(((GrParameter)parameter).isOptional());
      }

      res.addParameter(p);
    }
    
    return res;
  }
  
  public <T> T getData() {
    //noinspection unchecked
    return (T)myData;
  }

  @Nullable
  public static <T> T getData(@Nullable PsiElement method, @NotNull Object kind) {
    if (method instanceof GrLightMethodBuilder) {
      if (kind.equals(((GrLightMethodBuilder)method).myMethodKind)) {
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
    ((LightReferenceListBuilder)myThrowsList).addReference(type);
    return this;
  }

}
