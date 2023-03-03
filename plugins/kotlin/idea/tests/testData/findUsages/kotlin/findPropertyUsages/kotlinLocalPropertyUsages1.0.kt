// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtProperty
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: "val bar"


fun foo(): String {
    val <caret>bar = ""

    return bar
}

val bar = "1"
val x = bar
