// WITH_STDLIB
// AFTER-WARNING: Variable 'c' is never used
// AFTER-WARNING: Variable 'index' is never used
fun a() {
    val b = listOf(1,2,3,4,5)
    for (<caret>c : Int in b) {

    }
}