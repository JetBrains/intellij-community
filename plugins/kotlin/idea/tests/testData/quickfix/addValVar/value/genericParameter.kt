// "Add 'val' to parameter 'x'" "true"
// WITH_STDLIB
// COMPILER_ARGUMENTS: -XXLanguage:+GenericInlineClassParameter
@JvmInline
value class Foo<T>(<caret>x: T)
