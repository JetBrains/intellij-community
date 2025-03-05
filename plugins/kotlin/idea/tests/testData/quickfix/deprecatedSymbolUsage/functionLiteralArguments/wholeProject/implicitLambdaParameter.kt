// "Replace usages of 'minBy((T) -> R) on Iterable<T>: T?' in whole project" "true"
// K2_ACTION: "Replace usages of 'Iterable<T>.minBy((T) -> R): T?' in whole project" "true"
// WITH_STDLIB
// LANGUAGE_VERSION: 1.5
fun test() {
    listOf<Int>().minBy<caret> { it + 1 }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.replaceWith.DeprecatedSymbolUsageInWholeProjectFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.replaceWith.DeprecatedSymbolUsageInWholeProjectFix