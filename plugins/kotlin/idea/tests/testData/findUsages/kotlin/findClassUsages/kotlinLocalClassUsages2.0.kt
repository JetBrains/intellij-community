// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtClass
// OPTIONS: usages, constructorUsages
// PSI_ELEMENT_AS_TITLE: "class Bar"


fun foo(): Any {
    if (false) {
        class <caret>Bar

        return Bar()
    }

    return Bar()
}

class Bar

val x = Bar()
