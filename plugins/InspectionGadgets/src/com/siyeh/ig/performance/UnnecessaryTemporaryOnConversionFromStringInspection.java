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
package com.siyeh.ig.performance;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public class UnnecessaryTemporaryOnConversionFromStringInspection
        extends BaseInspection {

    /** @noinspection StaticCollection*/
    @NonNls private static final Map<String,String> s_conversionMap =
            new HashMap<String, String>(7);

    static {
        s_conversionMap.put("java.lang.Boolean", "valueOf");
        s_conversionMap.put("java.lang.Byte", "parseByte");
        s_conversionMap.put("java.lang.Double", "parseDouble");
        s_conversionMap.put("java.lang.Float", "parseFloat");
        s_conversionMap.put("java.lang.Integer", "parseInt");
        s_conversionMap.put("java.lang.Long", "parseLong");
        s_conversionMap.put("java.lang.Short", "parseShort");
    }

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "unnecessary.temporary.on.conversion.from.string.display.name");
    }

    public boolean isEnabledByDefault(){
        return true;
    }

    @NotNull
    public String buildErrorString(Object... infos) {
        final String replacementString =
                calculateReplacementExpression((PsiElement)infos[0]);
        return InspectionGadgetsBundle.message(
                "unnecessary.temporary.on.conversion.from.string.problem.descriptor",
                replacementString);
    }

    @Nullable
    @NonNls static String calculateReplacementExpression(
            PsiElement location) {
        final PsiMethodCallExpression expression =
                (PsiMethodCallExpression) location;
        final PsiReferenceExpression methodExpression =
                expression.getMethodExpression();
        final PsiExpression qualifierExpression =
                methodExpression.getQualifierExpression();
        if (!(qualifierExpression instanceof PsiNewExpression)) {
            return null;
        }
        final PsiNewExpression qualifier =
                (PsiNewExpression) qualifierExpression;
        final PsiExpressionList argumentList =
                qualifier.getArgumentList();
        if (argumentList == null) {
            return null;
        }
        final PsiExpression arg = argumentList.getExpressions()[0];
        final PsiType type = qualifier.getType();
        if (type == null) {
            return null;
        }
        final String qualifierType = type.getPresentableText();
        final String canonicalType = type.getCanonicalText();
        final String conversionName = s_conversionMap.get(canonicalType);
        if (TypeUtils.typeEquals("java.lang.Boolean", type)) {
            final LanguageLevel languageLevel = PsiUtil.getLanguageLevel(location);
            if (languageLevel.compareTo(LanguageLevel.JDK_1_5) < 0) {
                return qualifierType + '.' + conversionName + '(' +
                        arg.getText() + ").booleanValue()";
            } else {
                return qualifierType + ".parseBoolean(" +
                        arg.getText() + ')';
            }
        } else {
            return qualifierType + '.' + conversionName + '(' +
                    arg.getText() + ')';
        }
    }

    @Nullable
    public InspectionGadgetsFix buildFix(PsiElement location) {
        final String replacementExpression =
                calculateReplacementExpression(location);
        if (replacementExpression == null) {
            return null;
        }
        final String name = InspectionGadgetsBundle.message(
                "unnecessary.temporary.on.conversion.from.string.fix.name",
                replacementExpression);
        return new UnnecessaryTemporaryObjectFix(name);
    }

    private static class UnnecessaryTemporaryObjectFix
            extends InspectionGadgetsFix {

        private final String m_name;

        private UnnecessaryTemporaryObjectFix(
                String name) {
            m_name = name;
        }

        @NotNull
        public String getName() {
            return m_name;
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiMethodCallExpression expression =
                    (PsiMethodCallExpression) descriptor.getPsiElement();
            final String newExpression =
                    calculateReplacementExpression(expression);
            if (newExpression == null) {
                return;
            }
            replaceExpression(expression, newExpression);
        }
    }

    public BaseInspectionVisitor buildVisitor() {
        return new UnnecessaryTemporaryObjectVisitor();
    }

    private static class UnnecessaryTemporaryObjectVisitor
            extends BaseInspectionVisitor {

        /** @noinspection StaticCollection*/
        @NonNls private static final Map<String,String> s_basicTypeMap =
                new HashMap<String, String>(7);

        static {
            s_basicTypeMap.put("java.lang.Boolean", "booleanValue");
            s_basicTypeMap.put("java.lang.Byte", "byteValue");
            s_basicTypeMap.put("java.lang.Double", "doubleValue");
            s_basicTypeMap.put("java.lang.Float", "floatValue");
            s_basicTypeMap.put("java.lang.Integer", "intValue");
            s_basicTypeMap.put("java.lang.Long", "longValue");
            s_basicTypeMap.put("java.lang.Short", "shortValue");
        }

        @Override public void visitMethodCallExpression(
                @NotNull PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            final PsiReferenceExpression methodExpression =
                    expression.getMethodExpression();
            final String methodName = methodExpression.getReferenceName();
            final Map<String,String> basicTypeMap = s_basicTypeMap;
            if (!basicTypeMap.containsValue(methodName)) {
                return;
            }
            final PsiExpression qualifier =
                    methodExpression.getQualifierExpression();
            if (!(qualifier instanceof PsiNewExpression)) {
                return;
            }
            final PsiNewExpression newExp = (PsiNewExpression) qualifier;
            final PsiExpressionList argList = newExp.getArgumentList();
            if (argList== null) {
                return;
            }
            final PsiExpression[] args = argList.getExpressions();
            if (args.length != 1) {
                return;
            }
            final PsiType argType = args[0].getType();
            if (!TypeUtils.isJavaLangString(argType)) {
                return;
            }
            final PsiType type = qualifier.getType();
            if (type == null) {
                return;
            }
            final String typeText = type.getCanonicalText();
            if (!basicTypeMap.containsKey(typeText)) {
                return;
            }
            final String mappingMethod = basicTypeMap.get(typeText);
            if (!mappingMethod.equals(methodName)) {
                return;
            }
            registerError(expression, expression);
        }
    }
}