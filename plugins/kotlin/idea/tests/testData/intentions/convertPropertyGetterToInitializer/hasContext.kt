// COMPILER_ARGUMENTS: -Xcontext-parameters
// IS_APPLICABLE: false
// IGNORE_K1

context(c: String)
val v: Int
    <caret>get() = c.length
