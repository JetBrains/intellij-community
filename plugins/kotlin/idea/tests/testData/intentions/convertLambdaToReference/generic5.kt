// IS_APPLICABLE: false
// WITH_STDLIB
fun test() {
    foo <caret>{ bar<Int>() }
}

fun foo(f: () -> Unit) {}

fun <T> bar(): T = TODO()
