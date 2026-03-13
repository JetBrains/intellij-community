// COMPILER_ARGUMENTS: -Xexplicit-backing-fields
// PROBLEM: none
// K2_ERROR: Property must be initialized.
interface Parent
class Child : Parent

class Point {
    private val _prop = Child()

    var prop: Parent
        get() = _prop<caret>
}