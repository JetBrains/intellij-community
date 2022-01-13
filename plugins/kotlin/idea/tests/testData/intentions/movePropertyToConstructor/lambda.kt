// AFTER-WARNING: Parameter 'a' is never used, could be renamed to _
// AFTER-WARNING: Parameter 'b' is never used, could be renamed to _
class TestClass {
    val <caret>prop2 = { a: Int, b: String -> }
}