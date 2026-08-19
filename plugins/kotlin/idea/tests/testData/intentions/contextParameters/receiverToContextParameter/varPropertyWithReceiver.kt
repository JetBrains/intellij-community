// COMPILER_ARGUMENTS: -Xcontext-parameters

var <caret>String.foo: Int
    get() = length
    set(value) { println(this) }
