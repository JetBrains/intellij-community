open class Base {
    @JvmField
    protected val <caret>limit = 3
}

class Sub : Base() {
    fun useLimit() = limit
}
