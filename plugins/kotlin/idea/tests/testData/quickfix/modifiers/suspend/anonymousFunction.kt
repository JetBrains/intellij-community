// "Make containing function suspend" "false"
// ACTION: Convert to run
// ACTION: Convert to with
// ACTION: Do not show return expression hints
// DISABLE-ERRORS
class A {
    suspend fun foo() {}
}
val p = fun(a: A) {
    a.<caret>foo()
}