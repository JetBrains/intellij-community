// IS_APPLICABLE: false
// WITH_RUNTIME
fun test() {
    <caret>for (element in listOf(1, 2, 3)) {
        println(element)
        if (element == 1) break
    }
}