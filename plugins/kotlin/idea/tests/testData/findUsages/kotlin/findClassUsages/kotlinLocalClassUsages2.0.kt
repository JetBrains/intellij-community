// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtClass
// OPTIONS: usages, constructorUsages
// FIR_COMPARISON

fun foo(): Any {
    if (false) {
        class <caret>Bar

        return Bar()
    }

    return Bar()
}

class Bar

val x = Bar()
