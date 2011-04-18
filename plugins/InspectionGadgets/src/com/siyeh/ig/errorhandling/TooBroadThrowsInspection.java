/*
 * Copyright 2010 Bas Leijdekkers
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
package com.siyeh.ig.errorhandling;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ExceptionUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.*;

public class TooBroadThrowsInspection extends BaseInspection {

    @SuppressWarnings({"PublicField"})
    public boolean onlyWarnOnRootExceptions = false;

    @Override @NotNull
    public String getID() {
        return "OverlyBroadThrowsClause";
    }

    @Override @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "overly.broad.throws.clause.display.name");
    }

    @Override @NotNull
    protected String buildErrorString(Object... infos) {
        final List<PsiClass> typesMasked = (List<PsiClass>)infos[0];
        String typesMaskedString = typesMasked.get(0).getName();
        if (typesMasked.size() == 1) {
            return InspectionGadgetsBundle.message(
                    "overly.broad.throws.clause.problem.descriptor1",
                    typesMaskedString);
        } else {
            final int lastTypeIndex = typesMasked.size() - 1;
            for (int i = 1; i < lastTypeIndex; i++) {
                typesMaskedString += ", ";
                typesMaskedString += typesMasked.get(i).getName();
            }
            final String lastTypeString =
                    typesMasked.get(lastTypeIndex).getName();
            return InspectionGadgetsBundle.message(
                    "overly.broad.throws.clause.problem.descriptor2",
                    typesMaskedString, lastTypeString);
        }
    }

    @Override
    public JComponent createOptionsPanel() {
        return new SingleCheckboxOptionsPanel(
                InspectionGadgetsBundle.message("too.broad.catch.option"),
                this, "onlyWarnOnRootExceptions");
    }

    @NotNull
    @Override
    protected InspectionGadgetsFix buildFix(Object... infos) {
        final Collection<PsiClass> maskedExceptions =
                (Collection<PsiClass>)infos[0];
        final Boolean originalNeeded = (Boolean) infos[1];
        return new AddThrowsClauseFix(maskedExceptions,
                originalNeeded.booleanValue());
    }

    private static class AddThrowsClauseFix extends InspectionGadgetsFix {
        private final Collection<SmartPsiElementPointer<PsiClass>> types;
        private final boolean originalNeeded;

        AddThrowsClauseFix(@NotNull Collection<PsiClass> classes,
                           boolean originalNeeded) {
            types = new ArrayList<SmartPsiElementPointer<PsiClass>>();
          for (PsiClass type : classes) {
            types.add(SmartPointerManager.getInstance(type.getProject()).createSmartPsiElementPointer(type));
          }
            this.originalNeeded = originalNeeded;
        }

        @NotNull
        public String getName() {
            if (originalNeeded) {
                return InspectionGadgetsBundle.message(
                        "overly.broad.throws.clause.quickfix1");
            } else {
                return InspectionGadgetsBundle.message(
                        "overly.broad.throws.clause.quickfix2");
            }
        }

        @Override
        protected void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiElement element = descriptor.getPsiElement();
            final PsiElement parent = element.getParent();
            if (!(parent instanceof PsiReferenceList)) {
                return;
            }
            final PsiReferenceList referenceList = (PsiReferenceList) parent;
            final PsiElementFactory factory =
                    JavaPsiFacade.getElementFactory(project);
            if (!originalNeeded) {
                element.delete();
            }
            for (SmartPsiElementPointer<PsiClass> type : types) {
              PsiClass aClass = type.getElement();
              if (aClass == null) continue;
              final PsiJavaCodeReferenceElement referenceElement =
                        factory.createReferenceExpression(aClass);
                referenceList.add(referenceElement);
            }
        }
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new TooBroadThrowsVisitor();
    }

    private class TooBroadThrowsVisitor
            extends BaseInspectionVisitor {

        @Override
        public void visitMethod(PsiMethod method) {
            super.visitMethod(method);
            final PsiReferenceList throwsList = method.getThrowsList();
            final PsiJavaCodeReferenceElement[] throwsReferences =
                    throwsList.getReferenceElements();
            if (throwsReferences.length == 0){
                return;
            }
            final PsiCodeBlock body = method.getBody();
            if (body == null) {
                return;
            }
            final Set<PsiClassType> exceptionsThrown =
                    ExceptionUtils.calculateExceptionsThrown(body);
            final PsiClassType[] referencedExceptions =
                    throwsList.getReferencedTypes();
            final Set<PsiClassType> exceptionsDeclared =
                    new HashSet(referencedExceptions.length);
            ContainerUtil.addAll(exceptionsDeclared, referencedExceptions);
            final int referencedExceptionsLength = referencedExceptions.length;
            for (int i = 0; i < referencedExceptionsLength; i++) {
                final PsiClassType referencedException =
                        referencedExceptions[i];
                if (onlyWarnOnRootExceptions) {
                    if (!ExceptionUtils.isGenericExceptionClass(
                            referencedException)) {
                        continue;
                    }
                }
                final List<PsiClass> exceptionsMasked = new ArrayList<PsiClass>();
                for (PsiClassType exceptionThrown : exceptionsThrown) {
                    if (referencedException.isAssignableFrom(exceptionThrown) &&
                            !exceptionsDeclared.contains(exceptionThrown)) {
                      PsiClass aClass = exceptionThrown.resolve();
                      if (aClass != null) {
                        exceptionsMasked.add(aClass);
                      }
                    }
                }
                if (!exceptionsMasked.isEmpty()) {
                    final PsiJavaCodeReferenceElement throwsReference =
                            throwsReferences[i];
                    final boolean originalNeeded =
                            exceptionsThrown.contains(referencedException);
                    registerError(throwsReference, exceptionsMasked,
                            Boolean.valueOf(originalNeeded));
                }
            }
        }
    }
}
