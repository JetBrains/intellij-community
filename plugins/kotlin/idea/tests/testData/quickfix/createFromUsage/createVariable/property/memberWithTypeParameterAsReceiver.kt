// "Create member property 'bar'" "false"
// ACTION: Add 'n =' to argument
// ACTION: Create extension property 'T.bar'
// ACTION: Rename reference
// ERROR: Unresolved reference: bar
fun consume(n: Int) {}

fun <T> foo(t: T) {
    consume(t.<caret>bar)
}