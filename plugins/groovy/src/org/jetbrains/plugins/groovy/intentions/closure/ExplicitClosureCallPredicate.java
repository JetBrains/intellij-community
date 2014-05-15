/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.intentions.closure;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import org.jetbrains.plugins.groovy.lang.psi.util.ErrorUtil;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;

class ExplicitClosureCallPredicate implements PsiElementPredicate {

    @Override
    public boolean satisfiedBy(PsiElement element) {
        if (!(element instanceof GrMethodCallExpression)) {
            return false;
        }
        final GrMethodCallExpression call = (GrMethodCallExpression) element;
        final GrExpression invokedExpression = call.getInvokedExpression();
        if (invokedExpression == null) {
            return false;
        }
        if (!(invokedExpression instanceof GrReferenceExpression)) {
            return false;
        }
        final GrReferenceExpression referenceExpression = (GrReferenceExpression) invokedExpression;
        final String name = referenceExpression.getReferenceName();
        if (!"call".equals(name)) {
            return false;
        }
        final GrExpression qualifier = referenceExpression.getQualifierExpression();
        if (qualifier == null) {
            return false;
        }
        final PsiType qualifierType = qualifier.getType();
        if (qualifierType == null) {
            return false;
        }
        if (!qualifierType.equalsToText(GroovyCommonClassNames.GROOVY_LANG_CLOSURE)) {
            return false;
        }
        return !ErrorUtil.containsError(element);
    }
}
