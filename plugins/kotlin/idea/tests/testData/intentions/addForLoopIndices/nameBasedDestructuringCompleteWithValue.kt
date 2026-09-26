// WITH_STDLIB
// COMPILER_ARGUMENTS: -Xname-based-destructuring=complete
// AFTER-WARNING: Variable 'item' is never used
// AFTER-WARNING: Variable 'index' is never used
fun foo() {
    for (value<caret> in listOf("a", "b", "c")) {

    }
}