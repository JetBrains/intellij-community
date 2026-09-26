// IS_APPLICABLE: false
// COMPILER_ARGUMENTS: -Xexplicit-backing-fields

interface Parent
class Child : Parent

class Point {
    val p<caret>rop: Parent
    field = Child()
}