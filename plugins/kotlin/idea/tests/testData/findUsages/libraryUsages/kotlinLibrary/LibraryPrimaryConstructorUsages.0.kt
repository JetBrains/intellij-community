// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtPrimaryConstructor
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: "constructor A(Int)"
// FIND_BY_REF
// WITH_FILE_NAME

package usages

import library.*

class X : A {
    constructor(n: Int) : super(n)
}

class Y() : A(1)

fun test() {
    val a: A = A()
    val aa = <caret>A(1)
}

