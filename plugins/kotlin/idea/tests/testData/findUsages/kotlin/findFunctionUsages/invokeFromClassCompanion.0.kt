// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: "operator fun invoke()"

class Foo(i: Int) {
    companion object {
        operator fun inv<caret>oke() = 1
    }
}

fun f1() {
    Foo()
}

