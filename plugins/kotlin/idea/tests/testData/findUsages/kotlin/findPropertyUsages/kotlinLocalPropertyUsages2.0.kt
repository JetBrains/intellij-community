// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtProperty
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: "val bar"


fun foo(): String {
    if (true) {
        val <caret>bar = ""

        return bar
    }

    return bar
}

val bar = ""
val x = bar
