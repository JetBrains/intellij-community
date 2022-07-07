// PSI_ELEMENT: com.intellij.psi.PsiClass
// OPTIONS: derivedClasses
public class <caret>A: extends E {
    public A() {

    }
}

public class F: implements B {
    public F() {

    }
}
// FIR_COMPARISON