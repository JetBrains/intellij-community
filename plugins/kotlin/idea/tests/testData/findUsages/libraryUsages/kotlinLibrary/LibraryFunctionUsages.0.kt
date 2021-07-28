// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// OPTIONS: usages
// FIND_BY_REF

package usages

import library.*

fun test() {
    val f = ::foo
    <caret>foo()
}