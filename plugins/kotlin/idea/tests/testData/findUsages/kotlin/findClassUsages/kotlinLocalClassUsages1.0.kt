// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtClass
// OPTIONS: usages, constructorUsages
// FIR_COMPARISON

fun foo(): Any {
    class <caret>Bar

    return Bar()
}

class Bar

val x = Bar()
