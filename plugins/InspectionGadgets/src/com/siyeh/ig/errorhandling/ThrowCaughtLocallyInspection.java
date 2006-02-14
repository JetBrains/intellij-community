/*
 * Copyright 2003-2005 Dave Griffith
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

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.StatementInspection;
import com.siyeh.ig.StatementInspectionVisitor;

public class ThrowCaughtLocallyInspection extends StatementInspection {

    public String getGroupDisplayName() {
        return GroupNames.ERRORHANDLING_GROUP_NAME;
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ThrowCaughtLocallyVisitor();
    }

    private static class ThrowCaughtLocallyVisitor
            extends StatementInspectionVisitor {

        public void visitThrowStatement(PsiThrowStatement statement) {
            super.visitThrowStatement(statement);
            final PsiExpression exception = statement.getException();
            if (exception == null) {
                return;
            }
            final PsiType exceptionType = exception.getType();
            if (exceptionType == null) {
                return;
            }

            PsiTryStatement containingTryStatement =
                    PsiTreeUtil.getParentOfType(statement,
                            PsiTryStatement.class);
            while (containingTryStatement != null) {
                final PsiCodeBlock tryBlock =
                        containingTryStatement.getTryBlock();
                if (tryBlock == null) {
                    return;
                }
                if (PsiTreeUtil.isAncestor(tryBlock, statement, true)) {
                    final PsiParameter[] catchBlockParameters =
                            containingTryStatement.getCatchBlockParameters();
                    for (final PsiParameter parameter : catchBlockParameters) {
                        final PsiType parameterType = parameter.getType();
                        if (parameterType.isAssignableFrom(exceptionType)) {
                            registerStatementError(statement);
                            return;
                        }
                    }
                }
                containingTryStatement =
                        PsiTreeUtil.getParentOfType(containingTryStatement,
                                PsiTryStatement.class);
            }
        }
    }
}