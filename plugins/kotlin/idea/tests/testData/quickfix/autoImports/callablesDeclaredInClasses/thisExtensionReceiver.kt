// "Import extension function 'Int.ext'" "true"
package p

open class Foo {
    fun Int.ext() {}
}

object FooObject : Foo()

fun Int.anotherExt() {
    <caret>ext()
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ImportFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt.ImportQuickFix