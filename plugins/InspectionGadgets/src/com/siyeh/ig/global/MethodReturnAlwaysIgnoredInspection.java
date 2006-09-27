package com.siyeh.ig.global;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.reference.*;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.siyeh.ig.BaseGlobalInspection;
import com.siyeh.ig.psiutils.MethodInheritanceUtils;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public class MethodReturnAlwaysIgnoredInspection extends BaseGlobalInspection {

    private static final Logger LOG =
            Logger.getInstance("MethodReturnAlwaysIgnoredInspection");

    private static final Key<Boolean> ALWAYS_IGNORED = Key.create("ALWAYS_IGNORED_METHOD");

    public String getGroupDisplayName() {
        return GroupNames.CLASSLAYOUT_GROUP_NAME;
    }

    @Nullable
    public RefGraphAnnotator getAnnotator(RefManager refManager) {
        return new MethodIgnoredAnnotator();
    }

    public CommonProblemDescriptor[] checkElement(RefEntity refEntity, AnalysisScope scope,
                                                  InspectionManager manager, GlobalInspectionContext globalContext) {
        final CommonProblemDescriptor[] originalProblemDescriptors =
                super.checkElement(refEntity, scope, manager, globalContext);
        if (!(refEntity instanceof RefMethod)) {
            return null;
        }
        final RefMethod refMethod = (RefMethod) refEntity;

        if (methodReturnUsed(refMethod)) {
            markSiblings(refMethod);
            return originalProblemDescriptors;
        }
        if(!(refMethod.getElement() instanceof PsiMethod))
        {
            return originalProblemDescriptors;
        }
        final PsiMethod method = (PsiMethod) refMethod.getElement();
        if (method == null) {
            return originalProblemDescriptors;
        }
        if (MethodInheritanceUtils.inheritsFromLibraryMethod(method)) {
            markSiblings(refMethod);
            return originalProblemDescriptors;
        }

        final ProblemDescriptor descriptor = manager.createProblemDescriptor(method,
                InspectionGadgetsBundle.message("method.return.always.ignored.problem.descriptor"),
                (LocalQuickFix []) null,
                ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
        if (originalProblemDescriptors == null) {
            return new ProblemDescriptor[]{descriptor};
        } else {
            final int numDescriptors = originalProblemDescriptors.length;
            final ProblemDescriptor[] descriptors = new ProblemDescriptor[numDescriptors + 1];
            System.arraycopy(originalProblemDescriptors, 0, numDescriptors + 1,
                    0, numDescriptors);
            descriptors[numDescriptors] = descriptor;
            return descriptors;
        }
    }

    private void markSiblings(RefMethod refMethod) {
        final Set<RefMethod> siblingMethods =
                MethodInheritanceUtils.calculateSiblingMethods(refMethod);
        for (RefMethod siblingMethod : siblingMethods) {
            siblingMethod.putUserData(ALWAYS_IGNORED, false);
        }
    }

    private static boolean methodReturnUsed(RefMethod refMethod) {
        final Boolean alwaysIgnored = refMethod.getUserData(ALWAYS_IGNORED);
        return alwaysIgnored == null || !alwaysIgnored;
    }

    public boolean queryExternalUsagesRequests(InspectionManager manager,
                                               final GlobalInspectionContext globalContext,
                                               final ProblemDescriptionsProcessor processor) {
        final RefManager refManager = globalContext.getRefManager();
        refManager.iterate(new RefVisitor() {
            public void visitMethod(final RefMethod refMethod) {
                if (methodReturnUsed(refMethod)) {
                    return;
                }
                final GlobalInspectionContext.UsagesProcessor usagesProcessor =
                        new GlobalInspectionContext.UsagesProcessor() {
                            public boolean process(PsiReference psiReference) {
                                final PsiElement psiReferenceExpression = psiReference.getElement();
                                final PsiElement parent = psiReferenceExpression.getParent();
                                if (parent instanceof PsiMethodCallExpression &&
                                        !isIgnoredMethodCall((PsiCallExpression) parent)) {
                                    processor.ignoreElement(refMethod);
                                }
                                return false;
                            }
                        };
                globalContext.enqueueMethodUsagesProcessor(refMethod, usagesProcessor);
            }
        });
        return false;
    }

    private static boolean isIgnoredMethodCall
            (PsiCallExpression
                    methodExpression) {
        final PsiElement parent = methodExpression.getParent();
        return parent instanceof PsiExpressionStatement;
    }

    private static class MethodIgnoredAnnotator extends RefGraphAnnotator {
        public void onInitialize(RefElement refElement) {
            super.onInitialize(refElement);
            if (!(refElement instanceof RefMethod)) {
                return;
            }
            final RefMethod refMethod = (RefMethod) refElement;
            final PsiElement element = refElement.getElement();
            if (!(element instanceof PsiMethod)) {
                return;
            }
            final PsiMethod method = (PsiMethod) element;
            final PsiType returnType = method.getReturnType();
            if (PsiType.VOID.equals(returnType)) {
                return;
            }
            LOG.info("onInitialize:" + refMethod.getName());
            refElement.putUserData(ALWAYS_IGNORED, true);
        }

        public void onMarkReferenced(RefElement refWhat,
                                     RefElement refFrom,
                                     boolean referencedFromClassInitializer) {
            super.onMarkReferenced(refWhat, refFrom, referencedFromClassInitializer);
            if (!(refWhat instanceof RefMethod)) {
                return;
            }

            final RefMethod refMethod = (RefMethod) refWhat;

            if (methodReturnUsed(refMethod)) {
                return;
            }
            final PsiElement psiElement = refMethod.getElement();
            if (!(psiElement instanceof PsiMethod)) {
                return;
            }
            final PsiMethod psiMethod = (PsiMethod) psiElement;
            LOG.info("onMarkReferenced:" + refMethod.getName());

            final PsiElement element = refFrom.getElement();
            element.accept(new PsiRecursiveElementVisitor() {
                public void visitMethodCallExpression(PsiMethodCallExpression call) {
                    if (methodReturnUsed(refMethod)) {
                        return;
                    }
                    super.visitMethodCallExpression(call);
                    if (isIgnoredMethodCall(call)) {
                        return;
                    }
                    final PsiReferenceExpression methodExpression =
                            call.getMethodExpression();
                    if (methodExpression.isReferenceTo(psiMethod)) {
                        refMethod.putUserData(ALWAYS_IGNORED, false);
                    }
                }

                public void visitNewExpression(PsiNewExpression call) {
                    if (methodReturnUsed(refMethod)) {
                        return;
                    }
                    super.visitNewExpression(call);
                    if (isIgnoredMethodCall(call)) {
                        return;
                    }

                    final PsiMethod referedMethod = call.resolveMethod();
                    if (psiMethod.equals(referedMethod)) {
                        refMethod.putUserData(ALWAYS_IGNORED, false);
                    }
                }
            });
        }
    }


}
