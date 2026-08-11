// WITH_STDLIB
// COMPILER_ARGUMENTS: -Xname-based-destructuring=only-syntax
// AFTER-WARNING: Variable 'item' is never used
// AFTER-WARNING: Variable 'index' is never used
fun foo() {
    for (item<caret> in listOf("a", "b", "c")) {

    }
}