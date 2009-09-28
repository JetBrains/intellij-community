/*
 * Copyright 2009 Bas Leijdekkers
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

import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ClassUtils;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

import java.util.Set;
import java.util.HashSet;

public class ConstantJUnitAssertArgumentInspection extends BaseInspection {

    private static final Set<String> ASSERT_METHODS = new HashSet();

    static {
        ASSERT_METHODS.add("assertTrue");
        ASSERT_METHODS.add("assertFalse");
        ASSERT_METHODS.add("assertNull");
        ASSERT_METHODS.add("assertNotNull");
    }

    @Override
    @Nls
    @NotNull
    public String getDisplayName() {
        return "Constant JUnit assert argument";
    }

    @Override
    @NotNull
    protected String buildErrorString(Object... infos) {
        return "Argument <code>#ref</code> is constant";
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new ConstantJUnitAssertArugmentVisitor();
    }

    private static class ConstantJUnitAssertArugmentVisitor
            extends BaseInspectionVisitor {

        @Override
        public void visitMethodCallExpression(
                PsiMethodCallExpression expression) {
            final PsiReferenceExpression methodExpression =
                    expression.getMethodExpression();
            @NonNls final String methodName =
                    methodExpression.getReferenceName();
            if (!ASSERT_METHODS.contains(methodName)) {
                return;
            }
            final PsiMethod method = expression.resolveMethod();
            if (method == null) {
                return;
            }
            final PsiClass containingClass = method.getContainingClass();
            if (!ClassUtils.isSubclass(containingClass, "junit.framework.Assert") &&
                    !ClassUtils.isSubclass(containingClass, "org.junit.Assert")) {
                return;
            }
            final PsiExpressionList argumentList = expression.getArgumentList();
            final PsiExpression[] arguments = argumentList.getExpressions();
            if (arguments.length == 0) {
                return;
            }
            final PsiExpression lastArgument = arguments[arguments.length - 1];
            if (!PsiUtil.isConstantExpression(lastArgument)) {
                return;
            }
            registerError(lastArgument);
        }
    }
}
