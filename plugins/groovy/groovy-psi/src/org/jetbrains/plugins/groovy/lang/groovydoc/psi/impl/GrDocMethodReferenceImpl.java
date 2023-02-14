// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.lang.groovydoc.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PropertyUtilBase;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.*;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.processors.MethodResolverProcessor;

import java.util.Objects;

public class GrDocMethodReferenceImpl extends GrDocMemberReferenceImpl implements GrDocMethodReference {

  public GrDocMethodReferenceImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public String toString() {
    return "GrDocMethodReference";
  }

  @Override
  public void accept(@NotNull GroovyElementVisitor visitor) {
    visitor.visitDocMethodReference(this);
  }

  @Override
  @NotNull
  public GrDocMethodParams getParameterList() {
    GrDocMethodParams child = findChildByClass(GrDocMethodParams.class);
    assert child != null;
    return child;
  }

  @Override
  public PsiElement bindToText(Project project, String text) {
    GrDocComment comment = GroovyPsiElementFactory.getInstance(project).createDocCommentFromText(text);
    PsiElement tag = PsiTreeUtil.getChildOfType(comment, GrDocTag.class);
    PsiElement ref = PsiTreeUtil.getChildOfType(tag, GrDocMethodReference.class);
    assert ref != null : text;
    return replace(ref);
  }

  @Override
  public PsiElement resolve() {
    String name = getReferenceName();
    GrDocReferenceElement holder = getReferenceHolder();
    PsiElement resolved;
    if (holder != null) {
      GrCodeReferenceElement referenceElement = holder.getReferenceElement();
      resolved = referenceElement != null ? referenceElement.resolve() : null;
    } else {
      resolved = PsiUtil.getContextClass(this);
    }
    if (resolved instanceof PsiClass) {
      PsiType[] parameterTypes = getParameterList().getParameterTypes();
      PsiType thisType = JavaPsiFacade.getInstance(getProject()).getElementFactory().createType((PsiClass)resolved, PsiSubstitutor.EMPTY);

      MethodResolverProcessor processor = new MethodResolverProcessor(name, this, false, thisType, parameterTypes, PsiType.EMPTY_ARRAY);
      resolved.processDeclarations(processor, ResolveState.initial(), resolved, this);
      if (processor.hasApplicableCandidates()) {
        return processor.getCandidates()[0].getElement();
      }

      MethodResolverProcessor constructorProcessor =
        new MethodResolverProcessor(name, this, true, thisType, parameterTypes, PsiType.EMPTY_ARRAY);
      resolved.processDeclarations(constructorProcessor, ResolveState.initial(), resolved, this);
      if (constructorProcessor.hasApplicableCandidates()) {
        return constructorProcessor.getCandidates()[0].getElement();
      }
    }
    return null;
  }

  @Override
  protected ResolveResult[] multiResolveImpl() {
    String name = getReferenceName();
    GrDocReferenceElement holder = getReferenceHolder();
    PsiElement resolved;
    if (holder != null) {
      GrCodeReferenceElement referenceElement = holder.getReferenceElement();
      resolved = referenceElement != null ? referenceElement.resolve() : null;
    } else {
      resolved = PsiUtil.getContextClass(this);
    }
    if (resolved instanceof PsiClass) {
      PsiType[] parameterTypes = getParameterList().getParameterTypes();
      PsiType thisType = JavaPsiFacade.getInstance(getProject()).getElementFactory().createType((PsiClass) resolved, PsiSubstitutor.EMPTY);
      MethodResolverProcessor processor = new MethodResolverProcessor(name, this, false, thisType, parameterTypes, PsiType.EMPTY_ARRAY);
      MethodResolverProcessor constructorProcessor = new MethodResolverProcessor(name, this, true, thisType, parameterTypes, PsiType.EMPTY_ARRAY);
      resolved.processDeclarations(processor, ResolveState.initial(), resolved, this);
      resolved.processDeclarations(constructorProcessor, ResolveState.initial(), resolved, this);
      return ArrayUtil.mergeArrays(processor.getCandidates(), constructorProcessor.getCandidates());
    }
    return ResolveResult.EMPTY_ARRAY;
  }

  @Override
  public boolean isReferenceTo(@NotNull PsiElement element) {
    if (element instanceof PsiNamedElement && Objects.equals(((PsiNamedElement)element).getName(), getReferenceName())) {
      return getManager().areElementsEquivalent(element, resolve());
    }
    return false;
  }

  @Override
  public PsiElement handleElementRename(@NotNull String newElementName) throws IncorrectOperationException {
    final PsiElement resolved = resolve();
    if (resolved instanceof PsiMethod method) {
      final String oldName = getReferenceName();
      if (!method.getName().equals(oldName)) { //was property reference to accessor
        if (PropertyUtilBase.isSimplePropertyAccessor(method)) {
          final String newPropertyName = PropertyUtilBase.getPropertyName(newElementName);
          if (newPropertyName != null) {
            return super.handleElementRename(newPropertyName);
          }
        }
      }
    }
    return super.handleElementRename(newElementName);
  }

}
