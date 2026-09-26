// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: ""

class SomeJavaClass {
    public void run() {
        SomeClass some = new SomeClass();
        some.action();
    }
}
