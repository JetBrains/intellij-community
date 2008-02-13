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
package com.siyeh.ig.naming;

import com.intellij.psi.PsiClass;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.RenameFix;
import com.siyeh.ig.psiutils.ClassUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class ExceptionNameDoesntEndWithExceptionInspection
        extends BaseInspection {

    @NotNull
    public String getID() {
        return "ExceptionClassNameDoesntEndWithException";
    }

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "exception.name.doesnt.end.with.exception.display.name");
    }

    protected InspectionGadgetsFix buildFix(Object... infos) {
        return new RenameFix();
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "exception.name.doesnt.end.with.exception.problem.descriptor");
    }

    protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
        return true;
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ExceptionNameDoesntEndWithExceptionVisitor();
    }

    private static class ExceptionNameDoesntEndWithExceptionVisitor
            extends BaseInspectionVisitor {

        @Override public void visitClass(@NotNull PsiClass aClass) {
            // no call to super, so it doesn't drill down into inner classes
            final String className = aClass.getName();
            if (className == null) {
                return;
            }
            @NonNls final String exception = "Exception";
            if (className.endsWith(exception)) {
                return;
            }
            if (!ClassUtils.isSubclass(aClass, "java.lang.Exception")) {
                return;
            }
            registerClassError(aClass);
        }
    }
}