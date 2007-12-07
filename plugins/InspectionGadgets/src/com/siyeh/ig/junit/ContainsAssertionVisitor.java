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
package com.siyeh.ig.junit;

import com.intellij.psi.JavaRecursiveElementVisitor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiReferenceExpression;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

class ContainsAssertionVisitor extends JavaRecursiveElementVisitor {
    private boolean containsAssertion = false;

    @Override public void visitElement(@NotNull PsiElement element) {
        if (!containsAssertion) {
            super.visitElement(element);
        }
    }

    @Override public void visitMethodCallExpression(
            @NotNull PsiMethodCallExpression call) {
        if (containsAssertion) {
            return;
        }
        super.visitMethodCallExpression(call);
        final PsiReferenceExpression methodExpression =
                call.getMethodExpression();
        @NonNls final String methodName = methodExpression.getReferenceName();
        if (methodName == null) {
            return;
        }
        if (methodName.startsWith("assert") || methodName.startsWith("fail")) {
            containsAssertion = true;
        }
    }

    public boolean containsAssertion() {
        return containsAssertion;
    }
}
