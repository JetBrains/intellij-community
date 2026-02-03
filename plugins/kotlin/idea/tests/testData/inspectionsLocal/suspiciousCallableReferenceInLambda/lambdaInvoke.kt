// PROBLEM: none
// WITH_STDLIB

fun test() {
    val predicate = { _: String -> true }
    "".let {<caret> predicate::invoke }("123")
}