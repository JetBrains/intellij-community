// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtFunction
// OPTIONS: usages, expected
package foo

import foo.a1 as A1ImportAlias

actual fun a1<caret>() {}
fun checkJs() {
    A1ImportAlias()
}
