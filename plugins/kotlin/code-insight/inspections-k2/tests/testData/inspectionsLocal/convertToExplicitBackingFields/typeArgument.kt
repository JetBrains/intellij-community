// COMPILER_ARGUMENTS: -Xexplicit-backing-fields
class Foo {
    private val _x: MutableList<Int> = mutableListOf()
    val x: List<Any> get() = _x<caret>

    fun returnInt(): Int = _x[0]
}

