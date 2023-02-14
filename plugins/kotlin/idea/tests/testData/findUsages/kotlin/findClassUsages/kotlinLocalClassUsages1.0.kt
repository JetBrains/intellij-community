// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtClass
// OPTIONS: usages, constructorUsages


fun foo(): Any {
    class <caret>Bar

    return Bar()
}

class Bar

val x = Bar()
