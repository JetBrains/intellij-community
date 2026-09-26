// COMPILER_ARGUMENTS: -Xexplicit-backing-fields
// PROBLEM: none
sealed class Test {
    private val _items = mutableSetOf<Int>()

    open val items: Set<Int>
        get() = _items<caret>
}