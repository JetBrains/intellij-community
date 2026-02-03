// IS_APPLICABLE: false

class B : A() {
    init {
        <caret>setFoo("abc")
    }
}