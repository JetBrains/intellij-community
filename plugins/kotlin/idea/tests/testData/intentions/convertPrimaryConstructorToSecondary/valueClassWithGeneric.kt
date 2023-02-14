// IS_APPLICABLE: false
// COMPILER_ARGUMENTS: -XXLanguage:+GenericInlineClassParameter
// DISABLE-ERRORS
@JvmInline
value class C<T>(val <caret>x: T)