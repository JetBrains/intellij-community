// COMPILER_ARGUMENTS: -Xcontext-parameters

context(i: Int)
var <caret>String.foo: Int
    get() = i + length
    set(value) { println(this) }