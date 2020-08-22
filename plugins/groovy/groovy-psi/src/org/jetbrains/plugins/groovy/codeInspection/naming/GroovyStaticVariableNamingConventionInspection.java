/*
 * Copyright 2007-2008 Dave Griffith
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
package org.jetbrains.plugins.groovy.codeInspection.naming;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.codeInspection.GroovyFix;
import org.jetbrains.plugins.groovy.codeInspection.GroovyQuickFixFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;

public class GroovyStaticVariableNamingConventionInspection extends ConventionInspection {

    private static final int DEFAULT_MIN_LENGTH = 4;
    private static final int DEFAULT_MAX_LENGTH = 32;

    @Override
    protected GroovyFix buildFix(@NotNull PsiElement location) {
        return GroovyQuickFixFactory.getInstance().createRenameFix();
    }

    @Override
    protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
        return true;
    }

    @Override
    @NotNull
    public String buildErrorString(Object... args) {
        final String className = (String) args[0];
        if (className.length() < getMinLength()) {
            return GroovyBundle.message("inspection.message.static.variable.name.ref.too.short");
        } else if (className.length() > getMaxLength()) {
            return GroovyBundle.message("inspection.message.static.variable.name.ref.too.long");
        }
        return GroovyBundle.message("inspection.message.static.variable.name.ref.doesnt.match.regex", getRegex());
    }

    @Override
    protected String getDefaultRegex() {
        return "s_[a-z][A-Za-z\\d]*";
    }

    @Override
    protected int getDefaultMinLength() {
        return DEFAULT_MIN_LENGTH;
    }

    @Override
    protected int getDefaultMaxLength() {
        return DEFAULT_MAX_LENGTH;
    }

    @NotNull
    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new NamingConventionsVisitor();
    }


    private class NamingConventionsVisitor extends BaseInspectionVisitor {
        @Override
        public void visitField(@NotNull GrField grField) {
            super.visitField(grField);
            if (!grField.hasModifierProperty(PsiModifier.STATIC)) {
                return;
            }
            if (grField.hasModifierProperty(PsiModifier.FINAL)) {
                return;
            }
            final String name = grField.getName();
            if (isValid(name)) {
                return;
            }
            registerVariableError(grField, name);
        }
    }
}