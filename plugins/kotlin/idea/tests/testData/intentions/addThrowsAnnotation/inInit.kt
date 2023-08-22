// WITH_STDLIB
// IS_APPLICABLE: false

class X {
    init {
        <caret>throw RuntimeException()
    }
}
