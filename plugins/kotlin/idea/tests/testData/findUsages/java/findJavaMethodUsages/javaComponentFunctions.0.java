// PSI_ELEMENT: com.intellij.psi.PsiMethod
// OPTIONS: usages

public class JavaClass {
    public A <caret>component1() {
        return new A();
    }

    public int component2() {
        return 0;
    }
}

// IGNORE_K2_LOG