// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: "fun bar(Int): Int"
// FIND_BY_REF
// WITH_FILE_NAME

package usages

import library.*

fun test() {
    val f = A.T::bar
    A.T().<caret>bar(1)
}

