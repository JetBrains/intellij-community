// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// OPTIONS: usages

class SomeJavaClass {
    public void run() {
        SomeObject.INSTANCE.action();
    }
}
// FIR_COMPARISON