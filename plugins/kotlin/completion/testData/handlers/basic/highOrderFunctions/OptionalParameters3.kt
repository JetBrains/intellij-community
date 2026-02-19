fun xfoo(p: () -> Unit = {}){}

fun test() {
    xfo<caret>
}

// IGNORE_K2
// ELEMENT: xfoo
// TAIL_TEXT: " {...} (p: () -> Unit = ...) (<root>)"

