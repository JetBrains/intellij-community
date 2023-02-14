// "Add non-null asserted (!!) call" "true"
// WITH_STDLIB
fun test(s: String?) {
    s.run {
        <caret>length
    }
}