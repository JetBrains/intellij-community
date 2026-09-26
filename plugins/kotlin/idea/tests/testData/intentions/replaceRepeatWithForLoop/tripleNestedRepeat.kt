// WITH_STDLIB
fun foo() {
    <caret>repeat(2) { i ->
        repeat(3) { j ->
            repeat(4) { k ->
                println("$i $j $k")
            }
        }
    }
}