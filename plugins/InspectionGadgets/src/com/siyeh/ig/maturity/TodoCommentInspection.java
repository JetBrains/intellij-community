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
package com.siyeh.ig.maturity;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.InspectionGadgetsBundle;

public class TodoCommentInspection extends ClassInspection {
    public String getDisplayName() {
        return InspectionGadgetsBundle.message("todo.comment.display.name");
    }

    public String getGroupDisplayName() {
        return GroupNames.MATURITY_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return InspectionGadgetsBundle.message("todo.comment.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ClassWithoutToStringVisitor();
    }

    private static class ClassWithoutToStringVisitor extends BaseInspectionVisitor {
        public void visitComment(PsiComment comment) {
            super.visitComment(comment);
            if (TodoUtil.isTodoComment(comment)) {
                registerError(comment);
            }
        }

    }

}
