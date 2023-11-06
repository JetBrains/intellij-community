// IGNORE_FE10_BINDING_BY_FIR
// WITH_DIFFERENT_DATA_FOR_K2
fun test(foo: Int?, bar: Int): Int {
    var i = foo
    <caret>if (i == null) {
        return bar
    }
    return baz(i)
}

fun baz(i: Int?) = 1