// AFTER-WARNING: Parameter 'it' is never used, could be renamed to _
// AFTER-WARNING: Variable 'a' is never used
fun test() {
    val a: (Int) -> String = <caret>{ "" }
}