// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// CHECK_SUPER_METHODS_YES_NO_DIALOG: no
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: "fun getSmth(): String"

class SomeXImpl : SomeX {
    override fun <caret>getSmth(): String = TODO()
}

fun foo(x: SomeXImpl) = x.smth
