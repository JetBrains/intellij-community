// IS_APPLICABLE: false
// COMPILER_ARGUMENTS: -Xexplicit-backing-fields
// IGNORE_K1
interface Parent
class Child : Parent

class Point {
    val prop: Parent
    fie<caret>ld = Child()
}