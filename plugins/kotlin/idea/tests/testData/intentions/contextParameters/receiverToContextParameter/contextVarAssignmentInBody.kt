// COMPILER_ARGUMENTS: -Xcontext-parameters

class Cls {
    var i = 1
}

fun test() {
    Cls().foo = 2
}

var Cls<caret>.foo: Int
    get() = i
    set(value) { println(i + value) }
