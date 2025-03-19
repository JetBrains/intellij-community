// PSI_ELEMENT: com.intellij.psi.PsiField
// USAGE_FILTERING_RULES: com.intellij.usages.impl.rules.WriteAccessFilteringRule
public class Foo {
    public String <caret>foo;

    public Foo(String foo) {
        this.foo = foo;
    }
}

