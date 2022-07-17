// PSI_ELEMENT: com.intellij.psi.PsiMethod
// OPTIONS: overrides

import java.util.Collection;

abstract public class MyJavaCLass {
    public abstract void co<caret>ll(Collection c);
}

// FIR_COMPARISON