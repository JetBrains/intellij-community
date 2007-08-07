package org.jetbrains.plugins.groovy.intentions.conversions;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.InheritanceUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.intentions.base.ErrorUtil;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;


class IndexingMethodConversionPredicate implements PsiElementPredicate {
    public boolean satisfiedBy(PsiElement element) {
        if (!(element instanceof GrMethodCallExpression)) {
            return false;
        }

        if (ErrorUtil.containsError(element)) {
            return false;
        }
        final GrMethodCallExpression callExpression = (GrMethodCallExpression) element;
        final GrArgumentList argList = callExpression.getArgumentList();
        if (argList == null) {
            return false;
        }
        final GrExpression[] arguments = argList.getExpressionArguments();

        final GrExpression invokedExpression = callExpression.getInvokedExpression();
        if (!(invokedExpression instanceof GrReferenceExpression)) {
            return false;
        }
        final GrReferenceExpression referenceExpression = (GrReferenceExpression) invokedExpression;
        final GrExpression qualifier = referenceExpression.getQualifierExpression();
        if (qualifier == null) {
            return false;
        }
        final IElementType referenceType = referenceExpression.getDotTokenType();
        if (!GroovyTokenTypes.mDOT.equals(referenceType)) {
            return false;
        }
        final String methodName = referenceExpression.getName();
        if ("getAt".equals(methodName)) {
            return arguments.length == 1;
        }
        if ("get".equals(methodName)) {
            final PsiType qualifierType = qualifier.getType();
            if (!isMap(qualifierType)) {
                return false;
            }
            return arguments.length == 1;
        } else if ("setAt".equals(methodName)) {
            return arguments.length == 2;
        } else if ("put".equals(methodName)) {
            final PsiType qualifierType = qualifier.getType();
            if (!isMap(qualifierType)) {
                return false;
            }
            return arguments.length == 2;
        }
        return false;
    }

    private static boolean isMap(PsiType type) {
        if (type == null) {
            return false;
        }
        if (!(type instanceof PsiClassType)) {
            return false;
        }
        final PsiClass referentClass = ((PsiClassType) type).resolve();
        return isSubclass(referentClass, "java.util.Map");
    }

    public static boolean isSubclass(@Nullable PsiClass aClass,
                                     @NonNls String ancestorName) {
        if (aClass == null) {
            return false;
        }
        final PsiManager psiManager = aClass.getManager();
        final Project project = psiManager.getProject();
        final GlobalSearchScope scope = GlobalSearchScope.allScope(project);
        final PsiClass ancestorClass =
                psiManager.findClass(ancestorName, scope);
        return InheritanceUtil.isCorrectDescendant(aClass, ancestorClass, true);
    }

}
