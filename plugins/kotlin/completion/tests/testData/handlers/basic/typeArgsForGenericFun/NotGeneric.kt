// FIR_COMPARISON
fun Int.chain(): Int = this
fun test() {
    val chain = 3001.chain().<caret>
}

// ELEMENT: minus
// TAIL_TEXT: "(other: Int)"