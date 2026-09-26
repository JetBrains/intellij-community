// PRIORITY: LOW
// AFTER-WARNING: The value 'null' assigned to 'var someVar: Int? defined in test' is never used
// AFTER-WARNING: Variable 'someVar' is assigned but never accessed
fun test() {
    var someVar: <caret>Int? = 42
    someVar = null
}