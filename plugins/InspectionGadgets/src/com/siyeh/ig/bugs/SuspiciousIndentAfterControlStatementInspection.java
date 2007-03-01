/*
 * Copyright 2007 Bas Leijdekkers
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
package com.siyeh.ig.bugs;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nls;

public class SuspiciousIndentAfterControlStatementInspection
        extends BaseInspection {

  @Nls
  @NotNull
  public String getGroupDisplayName() {
    return null;
  }

  @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "suspicious.indent.after.control.statement.display.name");
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "suspicious.indent.after.control.statement.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor() {
        return new SuspiciousIndentAfterControlStatementVisitor();
    }

    private static class SuspiciousIndentAfterControlStatementVisitor
            extends BaseInspectionVisitor {

        public void visitWhileStatement(PsiWhileStatement statement) {
            super.visitWhileStatement(statement);
            checkLoopStatement(statement);
        }

        public void visitDoWhileStatement(PsiDoWhileStatement statement) {
            super.visitDoWhileStatement(statement);
            checkLoopStatement(statement);
        }

        public void visitForeachStatement(PsiForeachStatement statement) {
            super.visitForeachStatement(statement);
            checkLoopStatement(statement);
        }

        public void visitForStatement(PsiForStatement statement) {
            super.visitForStatement(statement);
            checkLoopStatement(statement);
        }

        public void visitIfStatement(PsiIfStatement statement) {
            super.visitIfStatement(statement);
            //final PsiStatement elseStatement = statement.getElseBranch();
            //if (elseStatement != null) {
            //    return;
            //}
            if (!isWhiteSpaceSuspicious(statement)) {
                return;
            }
            final PsiStatement nextStatement =
                    PsiTreeUtil.getNextSiblingOfType(statement,
                            PsiStatement.class);
            registerStatementError(nextStatement);
        }

        private void checkLoopStatement(PsiLoopStatement statement) {
            final PsiStatement body = statement.getBody();
            if (body instanceof PsiBlockStatement) {
                return;
            }
            if (!isWhiteSpaceSuspicious(statement)) {
                return;
            }
            final PsiStatement nextStatement =
                    PsiTreeUtil.getNextSiblingOfType(statement,
                            PsiStatement.class);
            registerStatementError(nextStatement);
        }

        private static boolean isWhiteSpaceSuspicious(PsiStatement statement) {
            final PsiElement prevSibling1 = statement.getPrevSibling();
            if (!(prevSibling1 instanceof PsiWhiteSpace)) {
                return false;
            }
            final PsiStatement nextStatement =
                    PsiTreeUtil.getNextSiblingOfType(statement,
                            PsiStatement.class);
            if (nextStatement == null) {
                return false;
            }
            final String text1 = prevSibling1.getText();
            final int index1 = getLineIndex(text1);
            final String indent1 = text1.substring(index1 + 1);
            if (indent1.length() == 0) {
                return false;
            }
            final PsiElement prevSibling2 = nextStatement.getPrevSibling();
            if (!(prevSibling2 instanceof PsiWhiteSpace)) {
                return false;
            }
            final String text2 = prevSibling2.getText();
            final int index2 = getLineIndex(text2);
            final String indent2 = text2.substring(index2 + 1);
            if (indent2.length() == 0) {
                return false;
            }
            return !indent1.equals(indent2);
        }

        private static int getLineIndex(String text) {
            final int newLineIndex1 = text.lastIndexOf('\n');
            final int carriageReturnIndex1 = text.lastIndexOf('\r');
            return Math.max(newLineIndex1, carriageReturnIndex1);
        }
    }
}
