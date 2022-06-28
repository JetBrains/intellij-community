// PSI_ELEMENT: com.intellij.psi.PsiMethod
// OPTIONS: usages
public class JavaClass {
    public int <caret>getSomething() {
        return 1;
    }

    public void setSomething(int value) {
    }
}
// FIR_COMPARISON