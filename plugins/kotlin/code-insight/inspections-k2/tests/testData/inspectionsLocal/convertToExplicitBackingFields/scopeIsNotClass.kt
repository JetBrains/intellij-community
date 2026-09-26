// COMPILER_ARGUMENTS: -Xexplicit-backing-fields
private val _items = mutableSetOf<Int>()
val items: Set<Int>
ge<caret>t() = _items

fun main() {
    _items.add(1)
}