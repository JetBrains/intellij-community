// COMPILER_ARGUMENTS: -Xexplicit-backing-fields
internal class Foo {
    private val _x = mutableListOf<Int>()

    /*
    Useful documentation!!!
     */
    val x: List<Int> get() = _x<caret>
}