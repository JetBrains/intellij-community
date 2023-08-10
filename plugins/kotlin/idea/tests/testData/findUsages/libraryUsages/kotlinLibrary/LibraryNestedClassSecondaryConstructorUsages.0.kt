// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtSecondaryConstructor
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: "constructor T()"
// FIND_BY_REF
// WITH_FILE_NAME

package usages

import library.*

class X : A.T {
    constructor() : super()
}

class Y() : A.T()

fun test() {
    val a: A.T = A.<caret>T()
    val aa = A.T(1)
}

