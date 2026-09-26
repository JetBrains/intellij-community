// COMPILER_ARGUMENTS: -Xexplicit-backing-fields
class Foo {
    val x: List<Any>
        field<caret>: MutableList<Int> = mutableListOf()

    fun returnInt(): Int = x[0]
}
