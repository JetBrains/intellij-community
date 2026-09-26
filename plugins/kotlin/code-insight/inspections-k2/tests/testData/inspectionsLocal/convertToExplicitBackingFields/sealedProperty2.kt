// COMPILER_ARGUMENTS: -Xexplicit-backing-fields
sealed class Test {
    private val _items = mutableSetOf<Int>()

    val items: Set<Int>
        get() = _items<caret>
}