// COMPILER_ARGUMENTS: -Xexplicit-backing-fields
// PROBLEM: none
// K2_ERROR: MUST_BE_INITIALIZED
interface Parent
class Child : Parent

class Point {
    private val _prop = Child()

    var prop: Parent
        get() = _prop<caret>
}