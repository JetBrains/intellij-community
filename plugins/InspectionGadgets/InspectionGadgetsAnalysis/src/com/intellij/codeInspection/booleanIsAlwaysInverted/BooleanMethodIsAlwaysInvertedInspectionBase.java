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
package com.intellij.codeInspection.booleanIsAlwaysInverted;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.reference.*;
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Processor;
import com.intellij.util.containers.hash.HashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Set;

class BooleanMethodIsAlwaysInvertedInspectionBase extends GlobalJavaBatchInspectionTool {
  private static final Key<Boolean> ALWAYS_INVERTED = Key.create("ALWAYS_INVERTED_METHOD");

  private static boolean hasNonInvertedCalls(final RefMethod refMethod) {
    final Boolean alwaysInverted = refMethod.getUserData(ALWAYS_INVERTED);
    if (alwaysInverted == null) return true;
    if (refMethod.isExternalOverride()) return true;
    if (refMethod.isReferenced() && !alwaysInverted.booleanValue()) return true;
    final Collection<RefMethod> superMethods = refMethod.getSuperMethods();
    for (RefMethod superMethod : superMethods) {
      if (hasNonInvertedCalls(superMethod)) return true;
    }
    return false;
  }

  private static void traverseSuperMethods(RefMethod refMethod,
                                           GlobalJavaInspectionContext globalContext,
                                           GlobalJavaInspectionContext.UsagesProcessor processor) {
    final Collection<RefMethod> superMethods = refMethod.getSuperMethods();
    for (RefMethod superMethod : superMethods) {
      traverseSuperMethods(superMethod, globalContext, processor);
    }
    globalContext.enqueueMethodUsagesProcessor(refMethod, processor);
  }

  private static void checkMethodCall(RefElement refWhat, final PsiElement element) {
    if (!(refWhat instanceof RefMethod)) return;
    final RefMethod refMethod = (RefMethod)refWhat;
    final PsiElement psiElement = refMethod.getElement();
    if (!(psiElement instanceof PsiMethod)) return;
    final PsiMethod psiMethod = (PsiMethod)psiElement;
    if (!PsiType.BOOLEAN.equals(psiMethod.getReturnType())) return;
    element.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression call) {
        super.visitMethodCallExpression(call);
        final PsiReferenceExpression methodExpression = call.getMethodExpression();
        if (methodExpression.isReferenceTo(psiMethod)) {
          if (isInvertedMethodCall(methodExpression)) return;
          refMethod.putUserData(ALWAYS_INVERTED, Boolean.FALSE);
        }
      }

      @Override
      public void visitMethodReferenceExpression(PsiMethodReferenceExpression expression) {
        super.visitMethodReferenceExpression(expression);
        if (expression.isReferenceTo(psiElement)) {
          refMethod.putUserData(ALWAYS_INVERTED, Boolean.FALSE);
        }
      }
    });
  }

  private static boolean isInvertedMethodCall(final PsiReferenceExpression methodExpression) {
    final PsiPrefixExpression prefixExpression = PsiTreeUtil.getParentOfType(methodExpression, PsiPrefixExpression.class);
    if (methodExpression.getQualifierExpression() instanceof PsiSuperExpression) return true; //don't flag super calls
    if (prefixExpression != null) {
      final IElementType tokenType = prefixExpression.getOperationTokenType();
      if (tokenType.equals(JavaTokenType.EXCL)) {
        return true;
      }
    }
    return false;
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionsBundle.message("boolean.method.is.always.inverted.display.name");
  }

  @Override
  @NotNull
  public String getGroupDisplayName() {
    return GroupNames.DATA_FLOW_ISSUES;
  }

  @Override
  @NotNull
  @NonNls
  public String getShortName() {
    return "BooleanMethodIsAlwaysInverted";
  }

  @Override
  @Nullable
  public RefGraphAnnotator getAnnotator(@NotNull final RefManager refManager) {
    return new BooleanInvertedAnnotator();
  }

  @Override
  public CommonProblemDescriptor[] checkElement(@NotNull RefEntity refEntity,
                                                @NotNull AnalysisScope scope,
                                                @NotNull final InspectionManager manager,
                                                @NotNull final GlobalInspectionContext globalContext) {
    if (refEntity instanceof RefMethod) {
      RefMethod refMethod = (RefMethod)refEntity;
      if (!refMethod.isReferenced()) return null;
      if (hasNonInvertedCalls(refMethod)) return null;
      if (!refMethod.getSuperMethods().isEmpty()) return null;
      final PsiMethod psiMethod = (PsiMethod)refMethod.getElement();
      final PsiIdentifier psiIdentifier = psiMethod.getNameIdentifier();
      if (psiIdentifier != null) {
        final Collection<RefElement> inReferences = refMethod.getInReferences();
        if (inReferences.size() == 1) {
          final RefElement refElement = inReferences.iterator().next();
          final PsiElement usagesContainer = refElement.getElement();
          if (usagesContainer == null) return null;
          if (ReferencesSearch.search(psiMethod, new LocalSearchScope(usagesContainer)).forEach(new Processor<PsiReference>() {
            private final Set<PsiReference> myFoundRefs = new HashSet<>();
            @Override
            public boolean process(PsiReference reference) {
              myFoundRefs.add(reference);
              return myFoundRefs.size() < 2;
            }
          })) return null;
        }
        return new ProblemDescriptor[]{manager.createProblemDescriptor(psiIdentifier,
                                                                       InspectionsBundle
                                                                         .message("boolean.method.is.always.inverted.problem.descriptor"),
                                                                       (LocalQuickFix)getQuickFix(null),
                                                                       ProblemHighlightType.GENERIC_ERROR_OR_WARNING, false)};
      }
    }
    return null;
  }

  @Override
  protected boolean queryExternalUsagesRequests(@NotNull final RefManager manager,
                                                @NotNull final GlobalJavaInspectionContext context,
                                                @NotNull final ProblemDescriptionsProcessor descriptionsProcessor) {
    manager.iterate(new RefJavaVisitor() {
      @Override
      public void visitMethod(@NotNull final RefMethod refMethod) {
        if (descriptionsProcessor.getDescriptions(refMethod) != null) { //suspicious method -> need to check external usages
          final GlobalJavaInspectionContext.UsagesProcessor usagesProcessor = new GlobalJavaInspectionContext.UsagesProcessor() {
            @Override
            public boolean process(PsiReference psiReference) {
              final PsiElement psiReferenceExpression = psiReference.getElement();
              if (psiReferenceExpression instanceof PsiReferenceExpression &&
                  !isInvertedMethodCall((PsiReferenceExpression)psiReferenceExpression)) {
                descriptionsProcessor.ignoreElement(refMethod);
              }
              return false;
            }
          };
          traverseSuperMethods(refMethod, context, usagesProcessor);
        }
      }
    });
    return false;
  }

  private static class BooleanInvertedAnnotator extends RefGraphAnnotator {
    @Override
    public void onInitialize(RefElement refElement) {
      if (refElement instanceof RefMethod) {
        final PsiElement element = refElement.getElement();
        if (!(element instanceof PsiMethod)) return;
        if (!PsiType.BOOLEAN.equals(((PsiMethod)element).getReturnType())) return;
        refElement.putUserData(ALWAYS_INVERTED, Boolean.TRUE); //initial mark boolean methods
      }
    }

    @Override
    public void onMarkReferenced(RefElement refWhat, RefElement refFrom, boolean referencedFromClassInitializer) {
      checkMethodCall(refWhat, refFrom.getElement());
    }
  }
}
