// IS_APPLICABLE: false
// DISABLE_ERRORS
interface A {
    abstract fun <caret>foo(): Int
}

class B : A {

}