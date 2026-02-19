// COMPILER_ARGUMENTS: -Xexplicit-backing-fields
private val _items = mutableSetOf<Int>()
val items: Set<Int>
    get() = _items<caret>