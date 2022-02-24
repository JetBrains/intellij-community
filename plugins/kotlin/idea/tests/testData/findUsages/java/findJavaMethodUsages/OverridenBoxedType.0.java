// PSI_ELEMENT: com.intellij.psi.PsiMethod
// OPTIONS: overrides
abstract public class MyJavaCLass {
    public abstract void <caret>meth(Integer i);
}

// FIR_COMPARISON