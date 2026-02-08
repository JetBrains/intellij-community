// COMPILER_ARGUMENTS: -Xexplicit-backing-fields

private val _items = mutableSetOf<Int>(/*COMMENT*/)
val items: Set<Int>
    get() = _items<caret>