// IS_APPLICABLE: false
// WITH_STDLIB
fun main() {
    outer@
    <caret>for (i in 1..100) {
        for (j in 1..100) {
            for (k in 1..1) {
                break@outer
            }
        }
    }
}