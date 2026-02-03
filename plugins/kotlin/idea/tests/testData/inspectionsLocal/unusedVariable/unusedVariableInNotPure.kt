// "Remove variable a (may change semantics)" "true"

var counter = 0
fun nextId(): Int {
    counter++          // side effect
    println(counter)   // I/O side effect
    return counter
}

fun test() {
    val a<caret> = nextId()
}
// IGNORE_K1