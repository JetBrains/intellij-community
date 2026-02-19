// WITH_STDLIB
fun foo() {
    val i = 0
    <caret>repeat(2) { j ->
        repeat(3) { k ->
            println("Nested loop iteration: $j, $k")
        }
    }
}