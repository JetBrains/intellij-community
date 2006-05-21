package com.siyeh.ig.bugs;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import org.jetbrains.annotations.NotNull;

public class PrimitiveArrayArgumentToVariableArgMethodInspection
        extends ExpressionInspection {

    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "primitive.array.argument.to.var.arg.method.display.name");
    }

    public String getGroupDisplayName() {
        return GroupNames.BUGS_GROUP_NAME;
    }

    @NotNull
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "primitive.array.argument.to.var.arg.method.problem.descriptor");
    }

    public boolean isEnabledByDefault() {
        return true;
    }

    public BaseInspectionVisitor buildVisitor() {
        return new PrimitiveArrayArgumentToVariableArgVisitor();
    }

    private static class PrimitiveArrayArgumentToVariableArgVisitor
            extends BaseInspectionVisitor {

        public void visitMethodCallExpression(
                @NotNull PsiMethodCallExpression call) {
            super.visitMethodCallExpression(call);

            final LanguageLevel languageLevel = PsiUtil.getLanguageLevel(call);
            if (languageLevel.compareTo(LanguageLevel.JDK_1_5) < 0) {
                return;
            }
            final PsiExpressionList argumentList = call.getArgumentList();
            final PsiExpression[] args = argumentList.getExpressions();
            if (args.length == 0) {
                return;
            }
            final PsiExpression lastArg = args[args.length - 1];
            if (!isPrimitiveArray(lastArg)) {
                return;
            }
            final PsiMethod method = call.resolveMethod();
            if (method == null) {
                return;
            }
            final PsiParameterList parameterList = method.getParameterList();
            final PsiParameter[] parameters = parameterList.getParameters();
            if (parameters == null) {
                return;
            }
            if (parameters.length != args.length) {
                return;
            }
            final PsiParameter lastParameter =
                    parameters[parameters.length - 1];
            if (!lastParameter.isVarArgs()) {
                return;
            }
            registerError(lastArg);
        }
    }

    private static boolean isPrimitiveArray(PsiExpression exp) {
        final PsiType type = exp.getType();
        if (type == null) {
            return false;
        }
        if (!(type instanceof PsiArrayType)) {
            return false;
        }
        final PsiType componentType = ((PsiArrayType)type).getComponentType();
        return TypeConversionUtil.isPrimitiveAndNotNull(componentType);
    }
}
