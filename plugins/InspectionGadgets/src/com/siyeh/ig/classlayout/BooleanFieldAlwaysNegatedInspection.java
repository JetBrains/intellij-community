/*
 * Copyright 2006-2011 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.classlayout;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.reference.*;
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseGlobalInspection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BooleanFieldAlwaysNegatedInspection extends BaseGlobalInspection {

    private static final Key<Boolean> ALWAYS_INVERTED = Key.create("ALWAYS_INVERTED_FIELD");

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message("boolean.field.always.negated.display.name");
    }

    @Nullable
    public RefGraphAnnotator getAnnotator(RefManager refManager) {
        return new BooleanInvertedAnnotator();
    }

    public CommonProblemDescriptor[] checkElement(RefEntity refEntity,
                                                  AnalysisScope scope,
                                                  InspectionManager manager,
                                                  GlobalInspectionContext globalContext) {
        if (!(refEntity instanceof RefField)) {
            return null;
        }
        final RefField refField = (RefField) refEntity;
        if (!refField.isReferenced()) {
            return null;
        }
        final Boolean alwaysInverted = refField.getUserData(ALWAYS_INVERTED);
        if (alwaysInverted == null || !alwaysInverted) {
            return null;
        }
        return new ProblemDescriptor[]{manager.createProblemDescriptor(refField.getElement(), InspectionGadgetsBundle.message(
                "boolean.field.always.negated.problem.descriptor"), false, (LocalQuickFix []) null,
                ProblemHighlightType.GENERIC_ERROR_OR_WARNING)};
    }

  protected boolean queryExternalUsagesRequests(final RefManager manager, final GlobalJavaInspectionContext context,
                                                final ProblemDescriptionsProcessor descriptionsProcessor) {
        manager.iterate(new RefJavaVisitor() {
            @Override public void visitField(final RefField refField) {
                if (descriptionsProcessor.getDescriptions(refField) != null) { //suspicious field -> need to check external usages
                  context.enqueueFieldUsagesProcessor(refField, new GlobalJavaInspectionContext.UsagesProcessor() {
                        public boolean process(PsiReference psiReference) {
                            final PsiElement psiReferenceExpression = psiReference.getElement();
                            if (psiReferenceExpression instanceof PsiReferenceExpression &&
                                    !isInvertedFieldRead((PsiReferenceExpression) psiReferenceExpression)) {
                                descriptionsProcessor.ignoreElement(refField);
                            }
                            return false;
                        }
                    });
                }
            }
        });
        return false;
    }


    private static class BooleanInvertedAnnotator extends RefGraphAnnotator {
        public void onInitialize(RefElement refElement) {
            if (!(refElement instanceof RefField)) {
                return;
            }
            final PsiElement element = refElement.getElement();
            if (!(element instanceof PsiField)) {
                return;
            }
            final PsiField field = (PsiField) element;
            if (!field.getType().equals(PsiType.BOOLEAN)) {
                return;
            }
            refElement.putUserData(ALWAYS_INVERTED, Boolean.TRUE); //initial mark boolean fields
        }

        public void onMarkReferenced(RefElement refWhat, RefElement refFrom, boolean referencedFromClassInitializer) {
            final PsiElement element = refFrom.getElement();
            if (!(refWhat instanceof RefField)) {
                return;
            }
            final RefField refField = (RefField) refWhat;
            final PsiElement psiElement = refField.getElement();
            if (!(psiElement instanceof PsiField)) {
                return;
            }
            final PsiField field = (PsiField) psiElement;
            final PsiType type = field.getType();
            if (!PsiType.BOOLEAN.equals(type)) {
                return;
            }
            element.accept(new JavaRecursiveElementVisitor() {

                @Override public void visitReferenceExpression(PsiReferenceExpression referenceExpression) {
                    super.visitReferenceExpression(referenceExpression);
                    if (referenceExpression.isReferenceTo(field)) {
                        if (isInvertedFieldRead(referenceExpression)) {
                            return;
                        }
                        refField.putUserData(ALWAYS_INVERTED, Boolean.FALSE);
                    }
                }

            });
        }
    }

    private static boolean isInvertedFieldRead(PsiReferenceExpression referenceExpression) {
        final PsiPrefixExpression prefixExpression = PsiTreeUtil.getParentOfType(referenceExpression, PsiPrefixExpression.class);
        if (prefixExpression != null) {
            final PsiJavaToken sign = prefixExpression.getOperationSign();
            final IElementType tokenType = sign.getTokenType();
            if (tokenType.equals(JavaTokenType.EXCL)) {
                return true;
            }
        }
        return isRead(referenceExpression);
    }

    private static boolean isRead(PsiElement element) {
        final PsiElement parent = element.getParent();
        if (!(parent instanceof PsiAssignmentExpression)) {
            return true;
        }
        final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression) parent;
        final PsiExpression rhs = assignmentExpression.getRExpression();
        return element.equals(rhs);
    }
}
