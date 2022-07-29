// PSI_ELEMENT: com.intellij.psi.PsiMethod
// OPTIONS: usages

public class JavaClassWI {
    public void <caret>invoke() {
    }

    public static JavaClassWI INSTANCE = new JavaClassWI();

    public static class Other extends JavaClassWI {
    }
}
// FIR_COMPARISON
// IGNORE_FIR_LOG