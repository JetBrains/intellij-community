// "Add 'fun' modifier to 'Function0'" "false"
// DISABLE_ERRORS
// ACTION: Add full qualifier
// ACTION: Convert to anonymous object
// ACTION: Split property declaration

fun test() {
    val x = <caret>Function0 {}
}
