/*
 * Copyright 2003-2007 Dave Griffith, Bas Leijdekkers
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

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ExceptionUtils;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class TooBroadCatchInspection extends BaseInspection {

    public String getID() {
        return "OverlyBroadCatchBlock";
    }

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message("too.broad.catch.display.name");
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        final PsiTryStatement tryStatement =
                PsiTreeUtil.getParentOfType((PsiElement)infos[0],
                        PsiTryStatement.class);
        assert tryStatement != null;
        final PsiCodeBlock tryBlock = tryStatement.getTryBlock();
        assert tryBlock != null;
        final Set<PsiType> exceptionsThrown =
                ExceptionUtils.calculateExceptionsThrown(tryBlock);
        final int numExceptionsThrown = exceptionsThrown.size();
        final Set<PsiType> exceptionsCaught =
                new HashSet<PsiType>(numExceptionsThrown);
        final PsiParameter[] parameters =
                tryStatement.getCatchBlockParameters();
        final List<String> typesMasked = new ArrayList<String>();
        for (final PsiParameter parameter : parameters) {
            final PsiType typeCaught = parameter.getType();
            if (exceptionsThrown.contains(typeCaught)) {
                exceptionsCaught.add(typeCaught);
            }
            if (parameter.equals(infos[0])) {
                for (PsiType typeThrown : exceptionsThrown) {
                    if (exceptionsCaught.contains(typeThrown)) {
                        //don't do anything
                    } else if (typeCaught.equals(typeThrown)) {
                        exceptionsCaught.add(typeCaught);
                    } else if (typeCaught.isAssignableFrom(typeThrown)) {
                        exceptionsCaught.add(typeCaught);
                        typesMasked.add(typeThrown.getPresentableText());
                    }
                }
            }
        }
        if (typesMasked.size() == 1) {
            return InspectionGadgetsBundle.message(
                    "too.broad.catch.problem.descriptor", typesMasked.get(0));
        } else {
            Collections.sort(typesMasked);
            String typesMaskedString = "";
            for (int i = 0; i < typesMasked.size() - 1; i++) {
                if (i != 0) {
                    typesMaskedString += ", ";
                }
                typesMaskedString += typesMasked.get(i);
            }
            return InspectionGadgetsBundle.message(
                    "too.broad.catch.problem.descriptor1",
                    typesMaskedString, typesMasked.get(typesMasked.size() - 1));
        }
    }

    public BaseInspectionVisitor buildVisitor() {
        return new TooBroadCatchVisitor();
    }

    private static class TooBroadCatchVisitor
            extends BaseInspectionVisitor {

        public void visitTryStatement(@NotNull PsiTryStatement statement) {
            super.visitTryStatement(statement);
            final PsiCodeBlock tryBlock = statement.getTryBlock();
            if (tryBlock == null) {
                return;
            }
            final Set<PsiType> exceptionsThrown =
                    ExceptionUtils.calculateExceptionsThrown(tryBlock);
            final int numExceptionsThrown = exceptionsThrown.size();
            final Set<PsiType> exceptionsCaught =
                    new HashSet<PsiType>(numExceptionsThrown);
            final PsiParameter[] parameters =
                    statement.getCatchBlockParameters();
            for (final PsiParameter parameter : parameters) {
                final PsiType typeCaught = parameter.getType();
                if (exceptionsThrown.contains(typeCaught)) {
                    exceptionsCaught.add(typeCaught);
                }
                for (PsiType typeThrown : exceptionsThrown) {
                    if (exceptionsCaught.contains(typeThrown)) {
                        //don't do anything
                    } else if (typeCaught.equals(typeThrown)) {
                        exceptionsCaught.add(typeCaught);
                    } else if (typeCaught.isAssignableFrom(typeThrown)) {
                        exceptionsCaught.add(typeCaught);
                        final PsiTypeElement typeElement =
                                parameter.getTypeElement();
                        registerError(typeElement, parameter);
                        return;
                    }
                }
            }
        }
    }
}