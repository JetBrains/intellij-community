// IGNORE_FE10_BINDING_BY_FIR
fun test(foo: Int?, bar: Int): Int? {
    var i = foo
    <caret>if (i == null) {
        return bar
    }
    return i
}