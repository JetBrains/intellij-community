// "Add 'return' expression" "true"
// WITH_STDLIB
fun test(): Boolean {
    foo()
}<caret>

fun foo() {
}