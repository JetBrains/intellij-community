// "Replace with 'this.minByOrNull(selector)'" "true"
// WITH_STDLIB
// LANGUAGE_VERSION: 1.5
class C<T> {
    fun test() {
        listOf(1).<caret>minBy { it }
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.replaceWith.DeprecatedSymbolUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.replaceWith.DeprecatedSymbolUsageFix