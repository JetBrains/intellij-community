// "Create member property 'bar'" "false"
// ERROR: Unresolved reference: bar
// K2_AFTER_ERROR: UNRESOLVED_REFERENCE
// K2_ERROR: UNRESOLVED_REFERENCE
fun consume(n: Int) {}

fun <T> foo(t: T) {
    consume(t.<caret>bar)
}