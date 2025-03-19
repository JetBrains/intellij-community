// "Replace with 'New'" "true"
package some

object New {
    fun foo() {}
}

@Deprecated("Use New", replaceWith = ReplaceWith("New"))
object Old {
    fun foo() {}
}

val test = <caret>Old.foo()
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.replaceWith.DeprecatedSymbolUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.replaceWith.DeprecatedSymbolUsageFix