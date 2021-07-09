// PROBLEM: Cast will always fail
// FIX: none
fun test(b: Boolean) {
    val x = if (b) X() else Y()
    val y = if (b) x <caret>as Y else X()
}
class X {}
class Y {}