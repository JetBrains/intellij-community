// COMPILER_ARGUMENTS: -Xexplicit-backing-fields
// PROBLEM: none
class Foo {
    private val _x by lazy { mutableListOf(1, 2, 3) }
    val x: List<Int> get() = _x<caret>
}