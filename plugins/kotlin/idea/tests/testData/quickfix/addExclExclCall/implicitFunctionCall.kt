// "Add non-null asserted (!!) call" "true"
// WITH_STDLIB
fun String?.foo() {
    <caret>toLowerCase()
}