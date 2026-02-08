// COMPILER_ARGUMENTS: -Xexplicit-backing-fields
interface Parent
class Child : Parent

class Point {
    private val _prop = Child()

    val `pro p`: Parent
        get<caret>() = _prop
}