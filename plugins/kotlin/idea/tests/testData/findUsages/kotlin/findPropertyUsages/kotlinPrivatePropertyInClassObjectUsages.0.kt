// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtProperty
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: "val t"


class Outer {
    val x = Outer.t

    companion object {
        private val <caret>t = 1
    }
}

