// WITH_STDLIB
fun foo() {
    <caret>for (i in 0..<2) {
        for (j in 0..<3) {
            println("Nested loop: $i, $j")
        }
    }
}