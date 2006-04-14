package com.siyeh.ig.global;

import com.siyeh.ig.BaseGlobalInspection;
import com.intellij.openapi.util.Key;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.reference.*;
import com.intellij.codeInspection.*;
import com.intellij.analysis.AnalysisScope;
import com.intellij.psi.*;
import org.jetbrains.annotations.Nullable;

public class MethodReturnAlwaysConstantInspection extends BaseGlobalInspection {
    private static final Key<Boolean> ALWAYS_CONSTANT = Key.create("ALWAYS_CONSTANT");

    public String getGroupDisplayName() {
        return GroupNames.CLASSLAYOUT_GROUP_NAME;
    }

    @Nullable
    public RefGraphAnnotator getAnnotator(RefManager refManager) {
        return new MethodIgnoredAnnotator();
    }

    public CommonProblemDescriptor[] checkElement(RefEntity refEntity, AnalysisScope scope, InspectionManager manager, final GlobalInspectionContext globalContext) {
        if (!(refEntity instanceof RefMethod)) {
            return null;
        }
        final RefMethod refMethod = (RefMethod) refEntity;
        final Boolean alwaysConstant = refMethod.getUserData(ALWAYS_CONSTANT);
        if (alwaysConstant == null || !alwaysConstant) {
            return null;
        }
        return new ProblemDescriptor[]{manager.createProblemDescriptor(refMethod.getElement(), InspectionsBundle.message(
                "method.return.always.constant.problem.descriptor"), (LocalQuickFix []) null,
                ProblemHighlightType.GENERIC_ERROR_OR_WARNING)};
    }

    public boolean queryExternalUsagesRequests(InspectionManager manager,
                                               final GlobalInspectionContext globalContext,
                                               final ProblemDescriptionsProcessor processor) {
        final RefManager refManager = globalContext.getRefManager();
        refManager.iterate(new RefVisitor() {
            public void visitMethod(final RefMethod refMethod) {
                if (processor.getDescriptions(refMethod) != null) { //suspicious method -> need to check external usages
                    final GlobalInspectionContext.UsagesProcessor usagesProcessor =
                            new GlobalInspectionContext.UsagesProcessor() {
                        public boolean process(PsiReference psiReference) {
                            final PsiElement psiReferenceExpression = psiReference.getElement();
                            final PsiElement parent = psiReferenceExpression.getParent();
                            if (parent instanceof PsiMethodCallExpression &&
                                    !MethodReturnAlwaysConstantInspection.isIgnoredMethodCall((PsiMethodCallExpression) parent)) {
                                processor.ignoreElement(refMethod);
                            }
                            return false;
                        }
                    };
                    globalContext.enqueueMethodUsagesProcessor(refMethod, usagesProcessor);
                }
            }
        });
        return false;
    }

    private static boolean isIgnoredMethodCall(PsiMethodCallExpression methodExpression) {
        final PsiElement parent = methodExpression.getParent();
        return parent instanceof PsiExpressionStatement;
    }

    private static class MethodIgnoredAnnotator extends RefGraphAnnotator {
        public void onInitialize(RefElement refElement) {
            if (!(refElement instanceof RefMethod)) {
                return;
            }
            final PsiElement element = refElement.getElement();
            if (!(element instanceof PsiMethod)) {
                return;
            }
            final PsiMethod method = (PsiMethod) element;
            final PsiType returnType = method.getReturnType();
            if (PsiType.VOID.equals(returnType)) {
                return;
            }
            refElement.putUserData(MethodReturnAlwaysConstantInspection.ALWAYS_CONSTANT, true);
        }

        public void onMarkReferenced(RefElement refWhat,
                                     RefElement refFrom,
                                     boolean referencedFromClassInitializer) {
            if (!(refWhat instanceof RefMethod)) {
                return;
            }
            final RefMethod refMethod = (RefMethod) refWhat;
            final PsiElement psiElement = refMethod.getElement();
            if (!(psiElement instanceof PsiMethod)) {
                return;
            }
            final PsiMethod psiMethod = (PsiMethod) psiElement;
            final PsiType returnType = psiMethod.getReturnType();
            if (returnType == null) {
                return;
            }
            if (PsiType.VOID.equals(returnType)) {
                return;
            }
            final PsiElement element = refFrom.getElement();
            element.accept(new PsiRecursiveElementVisitor() {
                public void visitMethodCallExpression(PsiMethodCallExpression call) {
                    super.visitMethodCallExpression(call);
                    final PsiReferenceExpression methodExpression = call.getMethodExpression();
                    if (methodExpression.isReferenceTo(psiMethod)) {
                        if (MethodReturnAlwaysConstantInspection.isIgnoredMethodCall(call)) {
                            return;
                        }
                        refMethod.putUserData(MethodReturnAlwaysConstantInspection.ALWAYS_CONSTANT, false);
                    }
                }
            });
        }
    }
}
