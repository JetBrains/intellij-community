// "Replace usages of 'maxBy((T) -> R) on Iterable<T>: T?' in whole project" "true"
// WITH_STDLIB
// LANGUAGE_VERSION: 1.5
import java.util.Collections

fun test() {
    val list = Collections.singletonList("a")
    list.maxBy<caret> { it.length }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.replaceWith.DeprecatedSymbolUsageInWholeProjectFix