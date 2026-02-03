// "Create parameter 'x'" "true"

open class B : A() {
    fun m() {
        c(42, "42", <caret>x = 42)
    }
}