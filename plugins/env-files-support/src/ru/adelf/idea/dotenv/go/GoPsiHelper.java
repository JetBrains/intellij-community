package ru.adelf.idea.dotenv.go;

import com.goide.inspections.GoInspectionUtil;
import com.goide.psi.*;
import com.goide.psi.impl.GoPsiUtil;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;

import java.util.Map;

class GoPsiHelper {

    private static final Map<String, Integer> ENV_FUNCTIONS = ContainerUtil.newHashMap(
            Pair.pair("getenv", 0),
            Pair.pair("setenv", 0),
            Pair.pair("lookupenv", 0),
            Pair.pair("unsetenv", 0)
    );

    /**
     * Checks if the call expression belongs to the stdlib os package and
     * it's a function which can work with environment variables, such as Getenv or Setenv.
     *
     * @param callExpression checking element
     * @return boolean
     */
    public static boolean checkEnvMethodCall(GoCallExpr callExpression) {
        GoReferenceExpression ref = GoPsiUtil.getCallReference(callExpression);
        if (ref == null) return false;

        String functionName = StringUtil.toLowerCase(ref.getIdentifier().getText());
        if (!ENV_FUNCTIONS.containsKey(functionName)) return false;

        PsiElement resolve = ref.resolve();
        GoFunctionOrMethodDeclaration declaration = ObjectUtils.tryCast(resolve, GoFunctionOrMethodDeclaration.class);
        if (!GoInspectionUtil.isInSdkPackage(declaration, "os")) return false;

        int position = ENV_FUNCTIONS.get(functionName);
        if (callExpression.getArgumentList().getExpressionList().size() < position+1) return false;

        GoExpression expr = callExpression.getArgumentList().getExpressionList().get(position);
        return expr instanceof GoStringLiteral;
    }
}
