/*
 * Copyright 2003-2008 Dave Griffith, Bas Leijdekkers
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

import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.RenameFix;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public class StandardVariableNamesInspection extends BaseInspection {

    @NonNls static final Map<String, String> s_expectedTypes =
            new HashMap<String, String>(13);
    @NonNls static final Map<String, String> s_boxingClasses =
            new HashMap<String, String>(8);

    static {
        s_expectedTypes.put("b", "byte");
        s_expectedTypes.put("c", "char");
        s_expectedTypes.put("ch", "char");
        s_expectedTypes.put("d", "double");
        s_expectedTypes.put("f", "float");
        s_expectedTypes.put("i", "int");
        s_expectedTypes.put("j", "int");
        s_expectedTypes.put("k", "int");
        s_expectedTypes.put("m", "int");
        s_expectedTypes.put("n", "int");
        s_expectedTypes.put("l", "long");
        s_expectedTypes.put("s", "java.lang.String");
        s_expectedTypes.put("str", "java.lang.String");

        s_boxingClasses.put("int", "java.lang.Integer");
        s_boxingClasses.put("short", "java.lang.Short");
        s_boxingClasses.put("boolean", "java.lang.Boolean");
        s_boxingClasses.put("long", "java.lang.Long");
        s_boxingClasses.put("byte", "java.lang.Byte");
        s_boxingClasses.put("float", "java.lang.Float");
        s_boxingClasses.put("double", "java.lang.Double");
        s_boxingClasses.put("char", "java.lang.Character");
    }

    @Override
    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "standard.variable.names.display.name");
    }

    @Override
    protected InspectionGadgetsFix buildFix(Object... infos) {
        return new RenameFix();
    }

    @Override
    protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
        return true;
    }

    @Override
    @NotNull
    public String buildErrorString(Object... infos) {
        final PsiVariable variable = (PsiVariable)infos[0];
        final String name = variable.getName();
        final String expectedType = s_expectedTypes.get(name);
        if (PsiUtil.isLanguageLevel5OrHigher(variable)) {
            final String boxedType = s_boxingClasses.get(expectedType);
            if (boxedType != null) {
                return InspectionGadgetsBundle.message(
                        "standard.variable.names.problem.descriptor2",
                        expectedType, boxedType);
            }
        }
        return InspectionGadgetsBundle.message(
                "standard.variable.names.problem.descriptor", expectedType);
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new StandardVariableNamesVisitor();
    }

    private static class StandardVariableNamesVisitor
            extends BaseInspectionVisitor {

        @Override public void visitVariable(@NotNull PsiVariable variable) {
            super.visitVariable(variable);
            final String variableName = variable.getName();
            final String expectedType = s_expectedTypes.get(variableName);
            if (expectedType == null) {
                return;
            }
            final PsiType type = variable.getType();
            final String typeText = type.getCanonicalText();
            if (expectedType.equals(typeText)) {
                return;
            }
            if (PsiUtil.isLanguageLevel5OrHigher(variable)) {
                final PsiPrimitiveType unboxedType =
                        PsiPrimitiveType.getUnboxedType(type);
                if (unboxedType != null) {
                    final String unboxedTypeText =
                            unboxedType.getCanonicalText();
                    if (expectedType.equals(unboxedTypeText)) {
                        return;
                    }
                }
            }
            registerVariableError(variable, variable);
        }
    }
}