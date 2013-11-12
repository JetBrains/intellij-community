/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.util.dynamicMembers;

import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.extensions.NamedArgumentDescriptor;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocComment;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrReflectedMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author Maxim.Medvedev
 */

public class GrDynamicMethodImpl extends LightElement implements GrMethod {
  protected final GrMethod myMethod;

  public GrDynamicMethodImpl(GrMethod method) {
    super(method.getManager(), method.getLanguage());
    myMethod = method;
  }

  public PsiClass getContainingClass() {
    return null;
  }

  public PsiType getReturnType() {
    return myMethod.getReturnType();
  }

  public PsiTypeElement getReturnTypeElement() {
    return myMethod.getReturnTypeElement();
  }

  public GrParameter[] getParameters() {
    return myMethod.getParameters();
  }

  @Override
  public String toString() {
    return "grails dynamic method";
  }

  public PsiIdentifier getNameIdentifier() {
    return myMethod.getNameIdentifier();
  }

  @NotNull
  public PsiMethod[] findSuperMethods() {
    return PsiMethod.EMPTY_ARRAY;
  }

  @NotNull
  public PsiMethod[] findSuperMethods(boolean checkAccess) {
    return PsiMethod.EMPTY_ARRAY;
  }

  @NotNull
  public PsiMethod[] findSuperMethods(PsiClass parentClass) {
    return PsiMethod.EMPTY_ARRAY;
  }

  @NotNull
  public List<MethodSignatureBackedByPsiMethod> findSuperMethodSignaturesIncludingStatic(boolean checkAccess) {
    return Collections.emptyList();
  }

  public PsiMethod findDeepestSuperMethod() {
    return null;
  }

  @NotNull
  public PsiMethod[] findDeepestSuperMethods() {
    return new PsiMethod[0];
  }

  public PsiElement copy() {
    return myMethod.copy();
  }

  public GrMember[] getMembers() {
    return new GrMember[0];
  }

  @NotNull
  public GrModifierList getModifierList() {
    return myMethod.getModifierList();
  }

  public boolean hasModifierProperty(@NonNls @NotNull String name) {
    return myMethod.hasModifierProperty(name);
  }

  @NotNull
  public Map<String, NamedArgumentDescriptor> getNamedParameters() {
    return myMethod.getNamedParameters();
  }

  @NotNull
  @Override
  public GrReflectedMethod[] getReflectedMethods() {
    return GrReflectedMethod.EMPTY_ARRAY;
  }

  public GrOpenBlock getBlock() {
    return null;
  }

  public void setBlock(GrCodeBlock newBlock) {

  }

  public GrTypeElement getReturnTypeElementGroovy() {
    return myMethod.getReturnTypeElementGroovy();
  }

  public PsiType getInferredReturnType() {
    return myMethod.getInferredReturnType();
  }

  @NotNull
  public String getName() {
    return myMethod.getName();
  }

  @NotNull
  public GrParameterList getParameterList() {
    return myMethod.getParameterList();
  }

  @NotNull
  public PsiReferenceList getThrowsList() {
    return myMethod.getThrowsList();
  }

  public PsiCodeBlock getBody() {
    return null;
  }

  public boolean isConstructor() {
    return false;
  }

  public boolean isVarArgs() {
    return myMethod.isVarArgs();
  }

  @NotNull
  public MethodSignature getSignature(@NotNull PsiSubstitutor substitutor) {
    return myMethod.getSignature(substitutor);
  }

  @NotNull
  public PsiElement getNameIdentifierGroovy() {
    return myMethod.getNameIdentifierGroovy();
  }

  public void accept(GroovyElementVisitor visitor) {
  }

  public void acceptChildren(GroovyElementVisitor visitor) {
  }

  public GrDocComment getDocComment() {
    return null;
  }

  public boolean isDeprecated() {
    return myMethod.isDeprecated();
  }

  public boolean hasTypeParameters() {
    return myMethod.hasTypeParameters();
  }

  public PsiTypeParameterList getTypeParameterList() {
    return myMethod.getTypeParameterList();
  }

  @NotNull
  public PsiTypeParameter[] getTypeParameters() {
    return myMethod.getTypeParameters();
  }

  public PsiElement setName(@NonNls @NotNull String name) throws IncorrectOperationException {
    return this;
  }

  @NotNull
  public HierarchicalMethodSignature getHierarchicalMethodSignature() {
    return myMethod.getHierarchicalMethodSignature();
  }

  @Override
  public boolean isEquivalentTo(PsiElement another) {
    return another instanceof GrDynamicMethodImpl && myMethod.isEquivalentTo(((GrDynamicMethodImpl)another).myMethod);
  }

  public GrTypeElement setReturnType(PsiType newReturnType) {
    throw new UnsupportedOperationException("Dynamic method can't change it's return type");
  }
}
