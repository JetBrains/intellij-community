// WITH_STDLIB
fun foo() {
    <caret>for (i in 0..<2) {
        for (j in 0..<3) {
            for (k in 0..<4) {
                println("$i $j $k")
            }
        }
    }
}