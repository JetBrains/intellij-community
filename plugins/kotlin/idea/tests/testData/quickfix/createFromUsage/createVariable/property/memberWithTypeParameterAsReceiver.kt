// "Create member property 'bar'" "false"
// ERROR: Unresolved reference: bar
// K2_AFTER_ERROR: Unresolved reference 'bar'.
fun consume(n: Int) {}

fun <T> foo(t: T) {
    consume(t.<caret>bar)
}