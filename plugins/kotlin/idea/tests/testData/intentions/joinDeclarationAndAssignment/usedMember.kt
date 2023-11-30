// IS_APPLICABLE: false
class A {
    private val <caret>a: Int
    private val b = 1

    init {
        a = b + b
    }
}