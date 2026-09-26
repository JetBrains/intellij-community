// COMPILER_ARGUMENTS: -Xcontext-parameters
// IS_APPLICABLE: false


context(c: String)
val v: Int
    <caret>get() = c.length
