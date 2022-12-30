// PROBLEM: none
// WITH_STDLIB
// COMPILER_ARGUMENTS: -XXLanguage:+GenericInlineClassParameter
@JvmInline
value class V<T> internal constructor(private val <caret>value: T)
