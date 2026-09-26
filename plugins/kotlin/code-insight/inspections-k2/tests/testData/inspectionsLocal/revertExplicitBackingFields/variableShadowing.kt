// COMPILER_ARGUMENTS: -Xexplicit-backing-fields

class A {
    val items: Set<Int>
        field<caret> = mutableSetOf()

    fun test() {
        val _items = mutableListOf<Int>()
        items.forEach(::println)
    }
}