// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// OPTIONS: usages
// CHECK_SUPER_METHODS_YES_NO_DIALOG: no
// PSI_ELEMENT_AS_TITLE: "fun someFun(): Unit"

open class Some {
    open fun someFun() {}
}

class SomeChild : Some() {
    override fun <caret>someFun() {}
}

fun useSome(s: Some, sc: SomeChild) {
    s.someFun()
    sc.someFun()
}