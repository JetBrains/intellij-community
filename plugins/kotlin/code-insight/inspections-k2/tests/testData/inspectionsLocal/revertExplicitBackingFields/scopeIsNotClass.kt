// COMPILER_ARGUMENTS: -Xexplicit-backing-fields

val items: Set<Int>
    fie<caret>ld = mutableSetOf<Int>()

fun main() {
    val _items = mutableListOf<Int>()
    items.size
}