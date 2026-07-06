// IS_APPLICABLE: false
// WITH_STDLIB
// ERROR: Inline class must have exactly one primary constructor parameter
// K2_ERROR: INLINE_CLASS_CONSTRUCTOR_WRONG_PARAMETERS_SIZE

@JvmInline
value class V()<caret>