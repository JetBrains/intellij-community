// WITH_STDLIB
fun foo() {
    <caret>for (x in sequenceOf(1, 2, 3)) {
        println(x)
    }
}