// COMPILER_ARGUMENTS: -Xcontext-parameters

class Cls {
    val i = 1
}

fun test() {
    Cls().foo
}

val Cls<caret>.foo: Int
    get() = i
