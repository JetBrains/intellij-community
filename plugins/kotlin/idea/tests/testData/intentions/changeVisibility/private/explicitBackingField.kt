// IS_APPLICABLE: false
// COMPILER_ARGUMENTS: -Xexplicit-backing-fields
// IGNORE_K1
interface Parent
class Child : Parent

class Point {
    v<caret>al prop: Parent
    field = Child()
}