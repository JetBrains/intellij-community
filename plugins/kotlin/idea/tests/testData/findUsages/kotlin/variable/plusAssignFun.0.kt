// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtProperty
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: "val c"
class C

operator fun C.plusAssign(p: Int) = Unit

fun foo() {
    val <caret>c = C()
    c += 10
}


