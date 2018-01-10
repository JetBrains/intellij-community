package ru.adelf.idea.dotenv.go;

import com.goide.psi.GoCallExpr;

class GoPsiHelper {

    /**
     * Checks os.getenv("") call
     * @param callExpression checking element
     * @return true if
     */
    static boolean checkGetMethodCall(GoCallExpr callExpression) {
        return callExpression.getExpression().getText().toLowerCase().equals("os.getenv");
    }
}
