fun foo(@Suppress("UNCHECKED_CAST") p: () -> Unit){}

fun bar() {
    foo(<caret>)
}
