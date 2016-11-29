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
package org.jetbrains.plugins.groovy.lang.psi.impl.synthetic;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightMethodBuilder;
import com.intellij.psi.impl.light.LightReferenceListBuilder;
import com.intellij.psi.impl.light.LightTypeParameterListBuilder;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.extensions.NamedArgumentDescriptor;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocComment;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierFlags;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrReflectedMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.util.GdkMethodUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GrInnerClassConstructorUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Max Medvedev
 */
public class GrReflectedMethodImpl extends LightMethodBuilder implements GrReflectedMethod {
  private static final Logger LOG = Logger.getInstance(GrReflectedMethodImpl.class);
  @NonNls public static final String CATEGORY_PARAMETER_NAME = "self";

  private final GrMethod myBaseMethod;
  private GrParameter[] mySkippedParameters = null;

  public GrReflectedMethodImpl(GrMethod baseMethod, GrParameter[] parameters, int optionalParams, PsiClassType categoryType) {
    super(baseMethod.getManager(), baseMethod.getLanguage(), baseMethod.getName(),
          new GrLightParameterListBuilder(baseMethod.getManager(), baseMethod.getLanguage()),
          new GrLightModifierList(baseMethod), new LightReferenceListBuilder(baseMethod.getManager(), baseMethod.getLanguage(), null),
          new LightTypeParameterListBuilder(baseMethod.getManager(), baseMethod.getLanguage())
    );

    initParameterList(parameters, optionalParams, categoryType);
    initTypeParameterList(baseMethod);
    initModifiers(baseMethod, categoryType != null);
    initThrowsList(baseMethod);
    setContainingClass(baseMethod.getContainingClass());
    setMethodReturnType(baseMethod.getReturnType());
    setConstructor(baseMethod.isConstructor());

    myBaseMethod = baseMethod;
  }

  private void initTypeParameterList(GrMethod method) {
    for (PsiTypeParameter parameter : method.getTypeParameters()) {
      addTypeParameter(parameter);
    }
  }

  private void initThrowsList(GrMethod baseMethod) {
    for (PsiClassType exception : baseMethod.getThrowsList().getReferencedTypes()) {
      addException(exception);
    }
  }

  private void initModifiers(GrMethod baseMethod, boolean isCategoryMethod) {
    final GrLightModifierList myModifierList = ((GrLightModifierList)getModifierList());

    for (String modifier : GrModifier.GROOVY_MODIFIERS) {
      if (baseMethod.getModifierList().hasExplicitModifier(modifier)) {
        myModifierList.addModifier(modifier);
      }
    }

    for (PsiAnnotation annotation : baseMethod.getModifierList().getRawAnnotations()) {
      final String qualifiedName = annotation.getQualifiedName();
      if (qualifiedName != null) {
        myModifierList.addAnnotation(qualifiedName);
      }
      else {
        myModifierList.addAnnotation(((GrAnnotation)annotation).getShortName());
      }
    }

    if (isCategoryMethod) {
      myModifierList.addModifier(PsiModifier.STATIC);
    }

    if (mySkippedParameters.length != 0) {
      myModifierList.removeModifier(GrModifierFlags.ABSTRACT_MASK);
    }
  }

  private void initParameterList(GrParameter[] parameters, int optionalParams, PsiClassType categoryType) {
    final GrLightParameterListBuilder parameterList = (GrLightParameterListBuilder)getParameterList();

    List<GrParameter> skipped = new ArrayList<>();

    if (categoryType != null) {
      parameterList.addParameter(new GrLightParameter(CATEGORY_PARAMETER_NAME, categoryType, this));
    }

    for (GrParameter parameter : parameters) {
      if (parameter.isOptional()) {
        if (optionalParams < 1) {
          skipped.add(parameter);
          continue;
        }
        optionalParams--;
      }
      parameterList.addParameter(createLightParameter(parameter));
    }

    LOG.assertTrue(optionalParams == 0);

    mySkippedParameters = skipped.toArray(new GrParameter[skipped.size()]);
  }

  private GrLightParameter createLightParameter(GrParameter parameter) {
    GrLightParameter lightParameter = new GrLightParameter(parameter.getName(), parameter.getDeclaredType(), this);
    lightParameter.setModifierList(parameter.getModifierList());
    return lightParameter;
  }

  @NotNull
  @Override
  public GrMethod getBaseMethod() {
    return myBaseMethod;
  }

  @NotNull
  @Override
  public GrParameter[] getSkippedParameters() {
    return mySkippedParameters;
  }

  @NotNull
  @Override
  public PsiElement getNavigationElement() {
    return myBaseMethod.getNavigationElement();
  }

  @Override
  public GrOpenBlock getBlock() {
    return myBaseMethod.getBlock();
  }

  @Override
  public void setBlock(GrCodeBlock newBlock) {
    throw new UnsupportedOperationException("synthetic method!");
  }

  @Override
  public GrTypeElement getReturnTypeElementGroovy() {
    return myBaseMethod.getReturnTypeElementGroovy();
  }

  @Override
  public PsiType getInferredReturnType() {
    return myBaseMethod.getInferredReturnType();
  }

  @Override
  public GrTypeElement setReturnType(@Nullable PsiType newReturnType) {
    throw new UnsupportedOperationException("synthetic method!");
  }

  @NotNull
  @Override
  public Map<String, NamedArgumentDescriptor> getNamedParameters() {
    return myBaseMethod.getNamedParameters();
  }

  @NotNull
  @Override
  public GrReflectedMethod[] getReflectedMethods() {
    return GrReflectedMethod.EMPTY_ARRAY;
  }

  @Override
  public GrMember[] getMembers() {
    return myBaseMethod.getMembers();
  }

  @NotNull
  @Override
  public PsiElement getNameIdentifierGroovy() {
    return myBaseMethod.getNameIdentifierGroovy();
  }

  @Override
  public GrParameter[] getParameters() {
    return getParameterList().getParameters();
  }

  @NotNull
  @Override
  public GrParameterList getParameterList() {
    return (GrParameterList)super.getParameterList();
  }

  @Override
  public void accept(GroovyElementVisitor visitor) {
    visitor.visitMethod(this);
  }

  @Override
  public void acceptChildren(GroovyElementVisitor visitor) {
    //todo
  }

  @Override
  public GrDocComment getDocComment() {
    return myBaseMethod.getDocComment();
  }

  @Override
  public String toString() {
    return getName() + " (" + StringUtil.join(getParameters(), f -> f.getType().getPresentableText() + " " + f.getName(), ", ") + ")";
  }

  @NotNull
  @Override
  public GrModifierList getModifierList() {
    return (GrModifierList)super.getModifierList();
  }

  @Override
  public Icon getIcon(int flags) {
    return myBaseMethod.getIcon(flags);
  }

  @Override
  public PsiIdentifier getNameIdentifier() {
    return PsiUtil.getJavaNameIdentifier(myBaseMethod);
  }

  @Override
  public boolean isPhysical() {
    return myBaseMethod.isPhysical();
  }

  @NotNull
  public static GrReflectedMethod[] createReflectedMethods(GrMethod method) {
    return CachedValuesManager.getCachedValue(method, () -> CachedValueProvider.Result.create(
      doCreateReflectedMethods(method, null, method.getParameters()), PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT
    ));
  }

  @NotNull
  public static GrReflectedMethod[] doCreateReflectedMethods(@NotNull GrMethod targetMethod,
                                                              @Nullable PsiClassType categoryType,
                                                              @NotNull GrParameter[] parameters) {
    int count = 0;
    for (GrParameter parameter : parameters) {
      if (parameter.isOptional()) count++;
    }

    if (count == 0 && categoryType == null) return GrReflectedMethod.EMPTY_ARRAY;

    final GrReflectedMethod[] methods = new GrReflectedMethod[count + 1];
    for (int i = 0; i <= count; i++) {
      methods[i] = new GrReflectedMethodImpl(targetMethod, parameters, count - i, categoryType);
    }

    return methods;
  }

  public static GrReflectedMethod[] createReflectedConstructors(GrMethod method) {
    assert method.isConstructor();

    PsiClass aClass = method.getContainingClass();
    if (aClass == null) return GrReflectedMethod.EMPTY_ARRAY;

    PsiClass enclosingClass = aClass.getContainingClass();
    if (enclosingClass != null && !aClass.hasModifierProperty(PsiModifier.STATIC)) {
      GrParameter[] parameters = GrInnerClassConstructorUtil
        .addEnclosingInstanceParam(method, enclosingClass, method.getParameterList().getParameters(), false);
      GrReflectedMethod[] reflectedMethods = doCreateReflectedMethods(method, null, parameters);
      if (reflectedMethods.length > 0) {
        return reflectedMethods;
      }
      else {
        return new GrReflectedMethod[]{new GrReflectedMethodImpl(method, parameters, 0, null)};
      }
    }
    else {
      return doCreateReflectedMethods(method, null, method.getParameters());
    }
  }

  @Nullable
  private static PsiClassType getCategoryType(GrMethod method) {
    final PsiClass containingClass = method.getContainingClass();
    if (containingClass == null) return null;
    return GdkMethodUtil.getCategoryType(containingClass);
  }

  @NotNull
  @Override
  public PsiElement getPrototype() {
    return getBaseMethod();
  }
}
