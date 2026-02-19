// FIR_IDENTICAL
// COMPILER_ARGUMENTS: -XXLanguage:+GenericInlineClassParameter
@JvmInline
value class <caret>V(val v: Int)

// MEMBER: "toString(): String"