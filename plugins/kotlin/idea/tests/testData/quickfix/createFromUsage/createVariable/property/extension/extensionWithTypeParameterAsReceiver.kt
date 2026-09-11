// "Create extension property 'T.bar'" "true"
// K2_ERROR: UNRESOLVED_REFERENCE
fun consume(n: Int) {}

fun <T> foo(t: T) {
    consume(t.<caret>bar)
}
// KTIJ-32974
// IGNORE_K2