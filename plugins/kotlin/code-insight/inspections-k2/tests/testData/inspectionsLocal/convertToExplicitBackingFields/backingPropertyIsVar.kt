// COMPILER_ARGUMENTS: -Xexplicit-backing-fields
// PROBLEM: none
class Foo {
    private var _x = mutableListOf<Int>()
    val x: List<Int> get() = _x<caret>
}