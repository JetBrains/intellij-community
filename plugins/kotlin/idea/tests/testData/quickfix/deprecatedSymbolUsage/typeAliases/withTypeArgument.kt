// "Replace with 'B<String>'" "true"
// K2_ACTION: "Replace with 'B<E>'" "true"
// WITH_STDLIB

@Deprecated(message = "renamed", replaceWith = ReplaceWith("B<E>"))
typealias A<E> = List<E>

typealias B<E> = List<E>

val x: <caret>A<String> = emptyList()

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.replaceWith.DeprecatedSymbolUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.replaceWith.DeprecatedSymbolUsageFix