// K2_ERROR: CONST_VAL_NOT_TOP_LEVEL_OR_OBJECT
open class Base {
    protected const val <caret>limit = 3
}

class Sub : Base() {
    fun useLimit() = limit
}
