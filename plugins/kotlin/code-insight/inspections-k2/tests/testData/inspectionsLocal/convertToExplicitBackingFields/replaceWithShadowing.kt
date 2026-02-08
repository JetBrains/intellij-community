// COMPILER_ARGUMENTS: -Xexplicit-backing-fields
interface Parent
class Child : Parent {
    override fun toString(): String = "child"
}

class Point {
    private val _prop = Child()

    val prop: Parent
    ge<caret>t() = _prop

    fun foo(): String {
        val prop = 1
        return _prop.toString()
    }
}