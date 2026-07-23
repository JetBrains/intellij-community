// WITH_STDLIB
fun foo() {
    <caret>for (`when` in 0..<3) {
        println(`when`)
    }
}
