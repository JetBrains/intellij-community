// WITH_STDLIB
// AFTER-WARNING: Parameter 'i' is never used, could be renamed to _
fun main() {
    <caret>for (i in 1..100) {
        for (j in 1..100) {
            for (k in 1..1) {
                break
            }
        }
    }
}