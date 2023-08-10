// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// OPTIONS: textOccurrences
// PSI_ELEMENT_AS_TITLE: "fun foo(Int, String): Unit"

package test

import library.Foo

fun test(f: Foo) {
    f.foo<caret>(3, "")
}

