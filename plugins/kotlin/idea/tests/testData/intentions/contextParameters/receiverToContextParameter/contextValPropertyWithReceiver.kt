// COMPILER_ARGUMENTS: -Xcontext-parameters
// IS_APPLICABLE: false

context(i: Int)
val <caret>String.foo: Int
    get() = i + length
