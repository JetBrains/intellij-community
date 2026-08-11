// COMPILER_ARGUMENTS: -Xexplicit-backing-fields
// PROBLEM: none
open class Test {
    private val _items = mutableSetOf<Int>()

    open val items: Set<Int>
        get() = _items<caret>
}
