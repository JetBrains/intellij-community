infix fun <A, B> A.to(that: B): Pair<A, B> = Pair(this, that)

fun foo() {
    val pair = 1 to<caret>
}

// IGNORE_K2
// ELEMENT: to
// TAIL_TEXT: "(that: B) (<root>) for A"
// CHAR: ' '