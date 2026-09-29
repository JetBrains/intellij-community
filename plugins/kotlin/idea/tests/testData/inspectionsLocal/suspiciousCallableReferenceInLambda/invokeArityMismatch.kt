// FIX: none
// WITH_STDLIB

fun test() {
    val predicate: (String, Int) -> Boolean = { _, _ -> true }
    "".let {<caret> predicate::invoke }
}
