// AFTER-WARNING: Variable 'i' is never used
fun <K> foo(n: K): K & Any = n!!

fun <T> bar(x: T) {
    var i: <caret>T & Any = foo(x)
}