// PSI_ELEMENT: com.intellij.psi.PsiMethod
// FIND_BY_REF
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: "constructor B(String)"
public class JJ extends B {
    public JJ(int i) {
        super("");
    }

    void test() {
        new <caret>B("");
    }
}


