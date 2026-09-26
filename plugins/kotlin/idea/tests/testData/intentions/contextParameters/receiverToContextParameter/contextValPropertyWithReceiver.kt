// COMPILER_ARGUMENTS: -Xcontext-parameters

context(i: Int)
val <caret>String.foo: Int
    get() = i + length
