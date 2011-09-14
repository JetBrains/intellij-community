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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.psi.impl.light.LightMethodBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocComment;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrConstructor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrReflectedMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Max Medvedev
 */
public class GrReflectedMethodImpl extends LightMethodBuilder implements GrReflectedMethod {
  private static final Logger LOG = Logger.getInstance(GrReflectedMethodImpl.class);

  private final GrMethod myBaseMethod;
  private GrParameter[] mySkippedParameters = null;

  public GrReflectedMethodImpl(GrMethod baseMethod, int optionalParams) {
    super(baseMethod.getManager(), baseMethod.getLanguage(), baseMethod.getName(),
          new GrLightParameterListBuilder(baseMethod.getManager(), baseMethod.getLanguage()),
          new GrLightModifierList(baseMethod)
    );
    initParameterList(baseMethod, optionalParams);

    initModifiers(baseMethod);
    initThrowsList(baseMethod);
    setContainingClass(baseMethod.getContainingClass());
    setMethodReturnType(baseMethod.getReturnType());
    setConstructor(baseMethod.isConstructor());

    myBaseMethod = baseMethod;
  }

  private void initThrowsList(GrMethod baseMethod) {
    for (PsiClassType exception : baseMethod.getThrowsList().getReferencedTypes()) {
      addException(exception);
    }
  }

  private void initModifiers(GrMethod baseMethod) {
    final GrModifierList modifierList = baseMethod.getModifierList();
    final GrLightModifierList myModifierList = ((GrLightModifierList)getModifierList());
    for (PsiElement modifier : modifierList.getModifiers()) {
      if (modifier instanceof GrAnnotation) {
        myModifierList.addAnnotation(((GrAnnotation)modifier).getQualifiedName());
      }
      else {
        myModifierList.addModifier(modifier.getText());
      }
    }
  }

  private void initParameterList(GrMethod baseMethod, int optionalParams) {
    final GrParameter[] parameters = baseMethod.getParameters();
    final GrLightParameterListBuilder parameterList = (GrLightParameterListBuilder)getParameterList();

    List<GrParameter> skipped = new ArrayList<GrParameter>();

    for (GrParameter parameter : parameters) {
      if (parameter.isOptional()) {
        if (optionalParams < 1) {
          skipped.add(parameter);
          continue;
        }
        optionalParams--;
      }
      parameterList.addParameter(new GrLightParameter(parameter.getName(), parameter.getType(), this));
    }

    LOG.assertTrue(optionalParams == 0, optionalParams + "methodText: " + baseMethod.getText());

    mySkippedParameters = skipped.toArray(new GrParameter[skipped.size()]);
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
    return myBaseMethod;
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
  public String[] getNamedParametersArray() {
    return myBaseMethod.getNamedParametersArray();
  }

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
    return "reflected method";
  }

  @NotNull
  @Override
  public GrModifierList getModifierList() {
    return (GrModifierList)super.getModifierList();
  }

  public static GrReflectedMethod[] createReflectedMethods(GrMethod method) {
    if (method instanceof LightElement) return GrReflectedMethod.EMPTY_ARRAY;

    if (method instanceof GrConstructor) return GrReflectedConstructorImpl.createReflectedMethods((GrConstructor)method);
    final GrParameter[] parameters = method.getParameters();
    int count = 0;
    for (GrParameter parameter : parameters) {
      if (parameter.isOptional()) count++;
    }

    if (count == 0) return GrReflectedMethod.EMPTY_ARRAY;

    final GrReflectedMethod[] methods = new GrReflectedMethod[count + 1];
    for (int i = 0; i <= count; i++) {
      methods[i] = new GrReflectedMethodImpl(method, count - i);
    }

    return methods;
  }
}
