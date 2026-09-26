// IS_APPLICABLE: false
// PROBLEM: none

data class XY(val x: Int, val y: Int)

fun create() = XY(1, 2)

class Foo {
    val xy = <caret>create()
}