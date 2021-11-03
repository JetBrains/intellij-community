// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// OPTIONS: usages

class SomeJavaClass {
    public void run() {
        SomeClass some = new SomeClass();
        some.action();
    }
}
// FIR_COMPARISON