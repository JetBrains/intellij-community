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
package com.siyeh.ig.classlayout;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import org.jetbrains.annotations.NotNull;

public class ClassInitializerInspection extends ClassInspection {

    public String getID() {
        return "NonStaticInitializer";
    }

    public String getGroupDisplayName() {
        return GroupNames.CLASSLAYOUT_GROUP_NAME;
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "class.initializer.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ClassInitializerVisitor();
    }

    private static class ClassInitializerVisitor extends BaseInspectionVisitor {

        // todo use visitClassInitializer()
        public void visitClass(@NotNull PsiClass aClass) {
            // no call to super, so that it doesn't drill down to inner classes
            final PsiClassInitializer[] initializers = aClass.getInitializers();
            for (final PsiClassInitializer initializer : initializers) {
                if (initializer.hasModifierProperty(PsiModifier.STATIC)) {
                    continue;
                }
                final PsiCodeBlock body = initializer.getBody();
                final PsiJavaToken leftBrace = body.getLBrace();
                if (leftBrace == null) {
                    continue;
                }
                registerError(leftBrace);
            }
        }
    }
}
