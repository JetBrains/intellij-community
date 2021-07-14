// FIR_IDENTICAL
fun test() {
    Test("text", "text")() // BUG
}

class Test(val x: String, val y: String) {
    operator fun invoke() {}
}
