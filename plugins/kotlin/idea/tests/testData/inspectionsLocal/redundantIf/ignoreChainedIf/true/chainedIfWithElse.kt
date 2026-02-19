// HIGHLIGHT: INFORMATION
fun b(x: Int): Boolean {
    return if (x > 20) {
        true
    } else <caret>if (x > 0) {
        true
    } else {
        false
    }
}