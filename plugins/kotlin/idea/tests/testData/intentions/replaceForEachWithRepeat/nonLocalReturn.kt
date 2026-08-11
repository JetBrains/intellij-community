// WITH_STDLIB
fun foo(): Int {
    (0..<5).<caret>forEach {
        if (it == 3) return 42
    }
    return 0
}