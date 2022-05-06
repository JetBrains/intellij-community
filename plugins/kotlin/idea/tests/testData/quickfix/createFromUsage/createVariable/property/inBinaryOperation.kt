// "Create property 'foo'" "false"
// ERROR: Unresolved reference: foo
// ACTION: Create extension function 'Int.foo'
// ACTION: Do not show return expression hints
// ACTION: Replace infix call with ordinary call
// WITH_STDLIB
fun refer() {
    1 <caret>foo 2
}