
// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtProperty
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: "val Bar"
fun foo(): Any {
    val Bar<caret> = object {}

    return Bar
}

object Bar {}

val x = Bar
