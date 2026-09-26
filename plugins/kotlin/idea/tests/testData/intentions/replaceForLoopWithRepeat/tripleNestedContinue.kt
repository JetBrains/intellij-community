// WITH_STDLIB
fun foo() {
    outer@ <caret>for (i in 0..<2) {
        for (j in 0..<3) {
            for (k in 0..<4) {
                if (k == 1) continue@outer
                println("$i $j $k")
            }
        }
    }
}