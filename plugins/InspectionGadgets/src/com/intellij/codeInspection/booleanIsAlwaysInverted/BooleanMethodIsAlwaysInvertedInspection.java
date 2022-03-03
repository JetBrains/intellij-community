// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.booleanIsAlwaysInverted;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.reference.*;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.refactoring.invertBoolean.InvertBooleanDelegate;
import com.intellij.refactoring.invertBoolean.InvertBooleanProcessor;
import com.intellij.util.CommonProcessors;
import com.intellij.util.ObjectUtils;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.fixes.InvertBooleanFix;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UDeclarationKt;
import org.jetbrains.uast.UMethod;

import java.util.Collection;

public class BooleanMethodIsAlwaysInvertedInspection extends GlobalJavaBatchInspectionTool {
  private static final Key<Boolean> ALWAYS_INVERTED = Key.create("ALWAYS_INVERTED_METHOD");

  @NotNull
  protected LocalQuickFix getInvertBooleanFix(boolean onTheFly) {
    return new InvertBooleanFix(InspectionGadgetsBundle.message("invert.method.quickfix")) {
      @Override
      public void doFix(@NotNull PsiElement element) {
        if (onTheFly) {
          //show the dialog, suggest to rename
          super.doFix(element);
        }
        else {
          PsiElement elementToRefactor = getElementToRefactor(element);
          new InvertBooleanProcessor(elementToRefactor, ((PsiNamedElement)elementToRefactor).getName())
            .run();
        }
      }
    };
  }

  @Override
  @NotNull
  public String getGroupDisplayName() {
    return InspectionsBundle.message("group.names.data.flow.issues");
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
    if (!(refEntity instanceof RefMethod)) return null;
    RefMethod refMethod = (RefMethod)refEntity;
    if (!refMethod.isReferenced() ||
        refMethod.isConstructor() ||
        hasNonInvertedCalls(refMethod) ||
        !refMethod.getSuperMethods().isEmpty()) return null;
    UMethod uMethod = (UMethod)refMethod.getUastElement();
    if (uMethod == null) return null;
    PsiElement anchor = UDeclarationKt.getAnchorPsi(uMethod);
    if (anchor != null) {
      final Collection<RefElement> inReferences = refMethod.getInReferences();
      if (inReferences.size() == 1) {
        final RefElement refElement = inReferences.iterator().next();
        final PsiElement usagesContainer = refElement.getPsiElement();
        if (usagesContainer == null) return null;
        if (ReferencesSearch.search(uMethod.getJavaPsi(), new LocalSearchScope(usagesContainer)).forEach(new CommonProcessors.FindFirstAndOnlyProcessor<>())) return null;
      }
      return new ProblemDescriptor[]{createProblemDescriptor(manager, anchor, false)};
    }
    return null;
  }

  protected ProblemDescriptor createProblemDescriptor(@NotNull InspectionManager manager,
                                                      PsiElement psiIdentifier,
                                                      boolean onTheFly) {
    return manager.createProblemDescriptor(psiIdentifier,
                                           JavaBundle.message("boolean.method.is.always.inverted.problem.descriptor"),
                                           InvertBooleanDelegate.findInvertBooleanDelegate(psiIdentifier.getParent()) != null ? getInvertBooleanFix(onTheFly) : null,
                                           ProblemHighlightType.GENERIC_ERROR_OR_WARNING, onTheFly);
  }

  @Override
  protected boolean queryExternalUsagesRequests(@NotNull final RefManager manager,
                                                @NotNull final GlobalJavaInspectionContext context,
                                                @NotNull final ProblemDescriptionsProcessor descriptionsProcessor) {
    manager.iterate(new RefJavaVisitor() {
      @Override
      public void visitMethod(@NotNull final RefMethod refMethod) {
        if (PsiModifier.PRIVATE.equals(refMethod.getAccessModifier())) return;
        if (descriptionsProcessor.getDescriptions(refMethod) != null) { //suspicious method -> need to check external usages
          final GlobalJavaInspectionContext.UsagesProcessor usagesProcessor = new GlobalJavaInspectionContext.UsagesProcessor() {
            @Override
            public boolean process(PsiReference psiReference) {
              final PsiReferenceExpression psiReferenceExpression = ObjectUtils.tryCast(psiReference.getElement(), PsiReferenceExpression.class);
              if (psiReferenceExpression == null) return false;
              PsiMethodCallExpression methodCallExpression =
                ObjectUtils.tryCast(psiReferenceExpression.getParent(), PsiMethodCallExpression.class);
              if (methodCallExpression != null && !isInvertedMethodCall(methodCallExpression)) {
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

  @Override
  public QuickFix getQuickFix(final String hint) {
    return getInvertBooleanFix(false);
  }

  @Override
  public LocalInspectionTool getSharedLocalInspectionTool() {
    return new BooleanMethodIsAlwaysInvertedLocalInspection(this);
  }

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
    final PsiElement psiElement = refMethod.getPsiElement();
    if (!(psiElement instanceof PsiMethod)) return;
    final PsiMethod psiMethod = (PsiMethod)psiElement;
    if (!PsiType.BOOLEAN.equals(psiMethod.getReturnType())) return;
    element.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression call) {
        super.visitMethodCallExpression(call);
        final PsiReferenceExpression methodExpression = call.getMethodExpression();
        if (methodExpression.isReferenceTo(psiMethod)) {
          if (isInvertedMethodCall(call)) return;
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

  static boolean isInvertedMethodCall(@NotNull PsiMethodCallExpression methodCallExpression) {
    PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
    if (methodExpression.getQualifierExpression() instanceof PsiSuperExpression) return true; //don't flag super calls
    final PsiPrefixExpression prefixExpression = ObjectUtils.tryCast(methodCallExpression.getParent(), PsiPrefixExpression.class);
    return prefixExpression != null && prefixExpression.getOperationTokenType().equals(JavaTokenType.EXCL);
  }

  private static class BooleanInvertedAnnotator extends RefGraphAnnotator {
    @Override
    public void onInitialize(RefElement refElement) {
      if (!(refElement instanceof RefMethod) || ((RefMethod)refElement).isConstructor()) return;
      final UMethod element = (UMethod)((RefMethod)refElement).getUastElement();
      if (!PsiType.BOOLEAN.equals(element.getReturnType())) return;
      refElement.putUserData(ALWAYS_INVERTED, Boolean.TRUE); //initial mark boolean methods
    }

    @Override
    public void onMarkReferenced(RefElement refWhat, RefElement refFrom, boolean referencedFromClassInitializer) {
      checkMethodCall(refWhat, refFrom.getPsiElement());
    }
  }
}