// PSI_ELEMENT: com.intellij.psi.PsiMethod
// OPTIONS: overrides

import java.util.Collection;

abstract public class MyJavaCLass {
    public abstract <T> void me<caret>th(Collection<T> c);
}

// FIR_COMPARISON