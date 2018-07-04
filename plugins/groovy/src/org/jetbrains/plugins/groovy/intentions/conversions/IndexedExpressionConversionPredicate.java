/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.plugins.groovy.intentions.conversions;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrIndexProperty;
import org.jetbrains.plugins.groovy.lang.psi.util.ErrorUtil;


class IndexedExpressionConversionPredicate implements PsiElementPredicate {
    @Override
    public boolean satisfiedBy(@NotNull PsiElement element) {
        if (!(element instanceof GrIndexProperty)) return false;

        if (ErrorUtil.containsError(element)) return false;

        final GrIndexProperty arrayIndexExpression = (GrIndexProperty) element;
        final PsiElement lastChild = arrayIndexExpression.getLastChild();
        if (!(lastChild instanceof GrArgumentList)) return false;

        final GrArgumentList argList = (GrArgumentList) lastChild;

        final GrExpression[] arguments = argList.getExpressionArguments();
        if (arguments.length != 1) return false;

        final PsiElement parent = element.getParent();
        if (!(parent instanceof GrAssignmentExpression)) {
            return true;
        }
        final GrAssignmentExpression assignmentExpression = (GrAssignmentExpression) parent;
        final GrExpression rvalue = assignmentExpression.getRValue();
        if (rvalue == null) return false;

        if (rvalue.equals(element)) return true;

        return !assignmentExpression.isOperatorAssignment();
    }

}
