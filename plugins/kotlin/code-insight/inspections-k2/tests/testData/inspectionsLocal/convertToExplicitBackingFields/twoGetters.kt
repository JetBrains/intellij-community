// COMPILER_ARGUMENTS: -Xexplicit-backing-fields
// PROBLEM: none
class Foo {
    private val _x get() = mutableListOf<Int>()
    val x: List<Int> get() = _x<caret>
}