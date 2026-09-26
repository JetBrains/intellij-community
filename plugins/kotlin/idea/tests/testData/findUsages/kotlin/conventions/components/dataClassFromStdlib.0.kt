// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtFunction
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: "operator fun component1(): A"
// FIND_BY_REF

fun foo(p: Pair<Int, Int>) {
    p.<caret>component1()
    val (x, y) = p
}

fun foo() {
    val (x, y) = 1 to "a"
}


