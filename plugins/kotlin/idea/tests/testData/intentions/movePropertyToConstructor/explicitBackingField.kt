// IS_APPLICABLE: false
// COMPILER_ARGUMENTS: -Xexplicit-backing-fields
// ERROR: Explicit backing field declarations are not supported in FE 1.0
// ERROR: Property must be initialized or be abstract
// K2_ERROR:
interface Parent
class Child : Parent

class Point {
    val prop: Parent
    fie<caret>ld = Child()
}