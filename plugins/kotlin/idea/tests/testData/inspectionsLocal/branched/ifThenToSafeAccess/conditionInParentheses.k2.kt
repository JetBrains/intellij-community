// HIGHLIGHT: WARNING
// FIX: Replace 'if' expression with safe access expression
class Some {
    fun bar() {}
}

fun Some?.foo() {
    <caret>if (((this) != null)) {
        bar()
    }
}