// FIR_IDENTICAL
// CHECK_SYMBOL_NAMES
// HIGHLIGHTER_ATTRIBUTES_KEY
fun test() {
    Test("text", "text")() // BUG
}

class Test(val x: String, val y: String) {
    operator fun invoke() {}
}
