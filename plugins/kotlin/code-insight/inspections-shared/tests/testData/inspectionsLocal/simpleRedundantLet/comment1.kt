// PROBLEM: none
// WITH_STDLIB
fun test() {
    val x = "hello"
    x.let<caret> {
        // comment
        it.length
    }
}