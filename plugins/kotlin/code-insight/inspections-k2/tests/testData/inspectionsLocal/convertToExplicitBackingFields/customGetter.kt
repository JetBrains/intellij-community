// COMPILER_ARGUMENTS: -Xexplicit-backing-fields
// PROBLEM: none
private val _x = mutableListOf<String>()
val x: List<String> get() {
    return _x + listOf("a", "b")
}<caret>