// WITH_STDLIB
fun foo() {
    <caret>for (it in 0..<5) {
        if (it == 3) continue
        println(it)
    }
}