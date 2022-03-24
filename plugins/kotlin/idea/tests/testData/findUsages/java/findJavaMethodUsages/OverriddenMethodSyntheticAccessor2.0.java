// PSI_ELEMENT: com.intellij.psi.PsiMethod
// OPTIONS: usages
public interface AI {
    String getFoo();
    void set<caret>Foo(String s);

    public class A implements AI {
        @Override
        public String getFoo() {
            return "";
        }

        @Override
        public void setFoo(String s) {
        }
    }
}

