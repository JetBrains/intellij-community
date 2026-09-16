// "Import extension function 'Int.ext'" "true"
// K2_ERROR: UNRESOLVED_REFERENCE
package p

open class Foo {
    fun Int.ext() {}
}

object FooObject : Foo()

fun Int.anotherExt() {
    <caret>ext()
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt.ImportQuickFix