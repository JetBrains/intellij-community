// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// OPTIONS: usages

class Foo(i: Int) {
    companion object {
        operator fun inv<caret>oke() = 1
    }
}

fun f1() {
    Foo()
}

// FIR_COMPARISON