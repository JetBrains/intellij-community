package ru.adelf.idea.dotenv.go;

import com.goide.psi.*;
import com.goide.psi.impl.GoPsiUtil;
import com.intellij.openapi.util.Pair;
import com.intellij.util.containers.ContainerUtil;

import java.util.Map;

class GoPsiHelper {

    private static final Map<String, Integer> ENV_FUNCTIONS = ContainerUtil.newHashMap(
            Pair.pair("os.getenv", 0),
            Pair.pair("os.setenv", 0),
            Pair.pair("os.lookupenv", 0),
            Pair.pair("os.unsetenv", 0)
    );

    /**
     * Checks if the call expression belongs to the stdlib os package and
     * it's a function which can work with environment variables, such as Getenv or Setenv.
     *
     * @param callExpression checking element
     * @return GoStringLiteral
     */
    static GoStringLiteral getEnvironmentGoLiteral(GoCallExpr callExpression) {
        GoReferenceExpression ref = GoPsiUtil.getCallReference(callExpression);
        if (ref == null) return null;

        /*String functionName = StringUtil.toLowerCase(ref.getIdentifier().getText());
        if (!ENV_FUNCTIONS.containsKey(functionName)) return false;

        PsiElement resolve = ref.resolve();
        GoFunctionOrMethodDeclaration declaration = ObjectUtils.tryCast(resolve, GoFunctionOrMethodDeclaration.class);
        if (!GoInspectionUtil.isInSdkPackage(declaration, "os")) return false;*/

        String functionName = ref.getText().toLowerCase();

        if (!ENV_FUNCTIONS.containsKey(functionName)) return null;

        int position = ENV_FUNCTIONS.get(functionName);
        if (callExpression.getArgumentList().getExpressionList().size() < position + 1) return null;

        GoExpression expr = callExpression.getArgumentList().getExpressionList().get(position);
        if(!(expr instanceof GoStringLiteral)) return null;

        return (GoStringLiteral) expr;
    }
}
