package ru.adelf.idea.dotenv.python;

import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PySubscriptionExpression;

class PythonPsiHelper {

    /**
     * Checks os.environ.get("") call
     * @param callExpression checking element
     * @return true if
     */
    static boolean checkGetMethodCall(PyCallExpression callExpression) {

        PyExpression callee = callExpression.getCallee();

        if(!(callee instanceof PyReferenceExpression)) {
            return false;
        }

        QualifiedName qualifiedName = ((PyReferenceExpression) (callee)).asQualifiedName();

        if(qualifiedName == null) {
            return false;
        }

        String name = qualifiedName.toString();

        return name != null && (name.equals("os.environ.get") || name.equals("os.getenv"));
    }

    /**
     * Checks os.environ[""] call
     * @param subscription checking element
     * @return true if
     */
    static boolean checkIndexCall(PySubscriptionExpression subscription) {
        QualifiedName qualifiedName = subscription.asQualifiedName();

        if(qualifiedName == null) {
            return false;
        }

        String name = qualifiedName.toString();

        return name != null && name.equals("os.environ.__getitem__");
    }
}
