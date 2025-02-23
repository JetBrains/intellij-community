// IS_APPLICABLE: true
// AFTER-WARNING: Parameter 't' is never used
fun foo() {
    bar<String>("x") <caret>{ it }
}

fun <T> bar(t:T, a: (Int) -> Int): Int {
    return a(1)
}
