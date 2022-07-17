// PROBLEM: none
// WITH_STDLIB
fun test(x: Any) {
    val s = run {
        <caret>x as? String ?: return
    }
}