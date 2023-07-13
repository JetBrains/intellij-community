// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: "fun <R> foo(??): R"
package usages

import library.foo

fun test() {
    foo<caret> {
        return@foo false
    }

    foo(fun(): Boolean { return@foo false })
}
