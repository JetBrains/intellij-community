// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: "operator fun invoke()"

fun f1() {
    SimpleInterface.invoke()
}

open class ClassWithInvoke {
    operator fun in<caret>voke() = 1
}

interface SimpleInterface {
    companion object : ClassWithInvoke()
}

