// WITH_STDLIB
fun foo() {
    (0..<2).<caret>forEach { i ->
        for (j in 0..<3) {
            println("$i $j")
        }
    }
}