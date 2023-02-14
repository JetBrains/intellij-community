// AFTER-WARNING: Parameter 'args' is never used
class Bar {
    operator fun get(vararg args: Int) {}
}

fun foo(a: Bar, i: Int) {
    a<caret>[i, 1]
}
