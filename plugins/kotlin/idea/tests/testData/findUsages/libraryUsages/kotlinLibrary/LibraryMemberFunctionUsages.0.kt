// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: "fun foo(Int): Int"
// FIND_BY_REF
// WITH_FILE_NAME

package usages

import library.*

fun test() {
    val f = A::foo
    A().<caret>foo(1)
}

