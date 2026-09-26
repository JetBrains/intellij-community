// COMPILER_ARGUMENTS: -Xexplicit-backing-fields
class Outer {
    val items: Set<Int>
    fie<caret>ld = mutableSetOf<Int>()

    inner class Nested {
        fun foo() {
            val _items = emptyList<Int>()
            print(items.size)
        }
    }
}