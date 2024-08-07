// IGNORE_K2
// COMPILER_ARGUMENTS: -XXLanguage:+GenericInlineClassParameter
@JvmInline
value class <caret>V(val v: Int)

// MEMBER: "toString(): String"