// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl;

import com.intellij.codeInsight.JavaTargetElementEvaluator;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMirrorElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.compiled.ClsMethodImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.completion.GrPropertyForCompletion;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrAnonymousClassDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.DefaultConstructor;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrRenameableLightElement;

import java.util.Collection;

import static com.intellij.util.containers.ContainerUtil.mapNotNull;
import static org.jetbrains.plugins.groovy.lang.resolve.CollapsingKt.collapseReflectedMethods;

/**
 * @author Maxim.Medvedev
 */
public class GroovyTargetElementEvaluator extends JavaTargetElementEvaluator {

  public static final Key<Object> NAVIGATION_ELEMENT_IS_NOT_TARGET = Key.create("GroovyTargetElementEvaluator.DONT_FOLLOW_NAVIGATION_ELEMENT");

  @Override
  public PsiElement getElementByReference(@NotNull PsiReference ref, int flags) {
    PsiElement sourceElement = ref.getElement();

    if (sourceElement instanceof GrCodeReferenceElement) {
      GrNewExpression newExpr;

      PsiElement parent = sourceElement.getParent();
      if (parent instanceof GrNewExpression) {
        newExpr = (GrNewExpression)parent;
      }
      else if (parent instanceof GrAnonymousClassDefinition) {
        PsiElement grandParent = parent.getParent();
        if (grandParent instanceof GrNewExpression) {
          newExpr = (GrNewExpression)grandParent;
        }
        else {
          return null;
        }
      }
      else {
        return null;
      }

      final PsiMethod constructor = newExpr.resolveMethod();
      if (constructor instanceof DefaultConstructor) return constructor.getContainingClass();

      final GrArgumentList argumentList = newExpr.getArgumentList();
      if (constructor != null &&
          argumentList != null &&
          PsiImplUtil.hasNamedArguments(argumentList) &&
          !PsiImplUtil.hasExpressionArguments(argumentList)) {
        if (constructor.getParameterList().isEmpty()) return constructor.getContainingClass();
      }

      return constructor;
    }

    if (sourceElement instanceof GrReferenceExpression) {
      PsiElement resolved = ((GrReferenceExpression)sourceElement).resolve();
       if (resolved instanceof GrGdkMethod || !(resolved instanceof GrRenameableLightElement)) {
        return correctSearchTargets(resolved);
      }
      return resolved;
    }

    return null;
  }

  @Nullable
  public static PsiElement correctSearchTargets(@Nullable PsiElement target) {
    if (target instanceof ClsMethodImpl) {
      PsiElement mirror = ((ClsMethodImpl)target).getSourceMirrorMethod();
      if (mirror != null) {
        return mirror.getNavigationElement();
      }
    }
    if (target != null && !(target instanceof GrAccessorMethod) && target.getUserData(NAVIGATION_ELEMENT_IS_NOT_TARGET) == null) {
      if (target instanceof PsiMirrorElement) {
        return ((PsiMirrorElement)target).getPrototype();
      }
      else {
        return target.getNavigationElement();
      }
    }
    return target;
  }

  @Nullable
  @Override
  public Collection<PsiElement> getTargetCandidates(@NotNull PsiReference reference) {
    if (reference instanceof GrReferenceExpression referenceExpression) {
      if (referenceExpression.hasMemberPointer()) {
        GroovyResolveResult[] results = referenceExpression.multiResolve(false);
        if (results.length > 0) {
          return collapseReflectedMethods(mapNotNull(results, it -> it.getElement()));
        }
      }
    }
    return super.getTargetCandidates(reference);
  }

  @Nullable
  @Override
  public PsiElement adjustTargetElement(Editor editor, int offset, int flags, @NotNull PsiElement targetElement) {
    if (targetElement instanceof GrPropertyForCompletion) {
      return ((GrPropertyForCompletion)targetElement).getOriginalAccessor();
    }
    return super.adjustTargetElement(editor, offset, flags, targetElement);
  }
}
