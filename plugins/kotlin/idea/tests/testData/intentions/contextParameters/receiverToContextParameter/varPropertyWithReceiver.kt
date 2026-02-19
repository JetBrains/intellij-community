// COMPILER_ARGUMENTS: -Xcontext-parameters
// IS_APPLICABLE: false

var <caret>String.foo: Int
    get() = length
    set(value) { println(this) }
