package com.siyeh.ig.numeric;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

public class NonReproducibleMathCallInspection extends ExpressionInspection {
    @SuppressWarnings("StaticCollection")
    private static final Set<String> nonReproducibleMethods = new HashSet<String>(20);

    static
    {
         nonReproducibleMethods.add("sin");
         nonReproducibleMethods.add("cos");
         nonReproducibleMethods.add("tan");
         nonReproducibleMethods.add("asin");
         nonReproducibleMethods.add("acos");
         nonReproducibleMethods.add("atan");
         nonReproducibleMethods.add("exp");
         nonReproducibleMethods.add("log");
         nonReproducibleMethods.add("log10");
         nonReproducibleMethods.add("cbrt");
         nonReproducibleMethods.add("sinh");
         nonReproducibleMethods.add("cosh");
         nonReproducibleMethods.add("tanh");
         nonReproducibleMethods.add("expm1");
         nonReproducibleMethods.add("log1p");
         nonReproducibleMethods.add("atan2");
         nonReproducibleMethods.add("pow");
         nonReproducibleMethods.add("hypot");
    }
    private final MakeStrictFix fix = new MakeStrictFix();

    public String getDisplayName() {
        return "Non-reproducible call to java.lang.Math";
    }

    public String getGroupDisplayName() {
        return GroupNames.NUMERIC_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Math.#ref() may produce non-reproducible results #loc";
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    private static class MakeStrictFix extends InspectionGadgetsFix {
        public String getName() {
            return "Replace with StrictMath call";
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException{
            final PsiIdentifier nameIdentifier =
                    (PsiIdentifier) descriptor.getPsiElement();
            final PsiReferenceExpression reference =
                    (PsiReferenceExpression) nameIdentifier.getParent();
            assert reference != null;
            final String name = reference.getReferenceName();
            replaceExpression(reference, "StrictMath."+name);
        }
    }

    public BaseInspectionVisitor buildVisitor() {
        return new BigDecimalEqualsVisitor();
    }

    private static class BigDecimalEqualsVisitor extends BaseInspectionVisitor {

        public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            final PsiReferenceExpression methodExpression =
                    expression.getMethodExpression();
            if(methodExpression == null)
            {
                return;
            }
            final String methodName = methodExpression.getReferenceName();
            if(!nonReproducibleMethods.contains(methodName))
            {
                return;
            }
            final PsiMethod method = expression.resolveMethod();
            if(method == null)
            {
                return;
            }
            final PsiClass referencedClass = method.getContainingClass();
            if(referencedClass == null)
            {
                return;
            }
            final String className = referencedClass.getQualifiedName();
            if(!"java.lang.Math".equals(className))
            {
                return;
            }
            registerMethodCallError(expression);
        }

    }

}
