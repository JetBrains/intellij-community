// PROBLEM: none
// WITH_STDLIB
fun test() {
    val x = "hello"
    x.let<caret> {
        /** Document comment */
        it.length
    }
}