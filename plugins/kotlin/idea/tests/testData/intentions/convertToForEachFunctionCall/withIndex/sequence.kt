// WITH_RUNTIME
fun test() {
    <caret>for ((index, element) in sequenceOf(1, 2, 3).withIndex()) {
        println("$index:$element")
    }
}