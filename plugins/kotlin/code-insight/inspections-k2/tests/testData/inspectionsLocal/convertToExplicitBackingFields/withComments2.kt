// COMPILER_ARGUMENTS: -Xexplicit-backing-fields
internal class Foo {
    private val _x = mutableListOf<Int>() // comment
    // comment 2
    val x: List<Int>
        // comment 3
        get() = _x<caret>
}