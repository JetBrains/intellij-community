// COMPILER_ARGUMENTS: -Xexplicit-backing-fields
// PROBLEM: none

class Holder

private val _items = mutableSetOf<Int>()

val Holder.items: Set<Int>
    get() = <caret>_items