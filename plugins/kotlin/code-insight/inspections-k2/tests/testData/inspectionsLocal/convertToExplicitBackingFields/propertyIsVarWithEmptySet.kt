// COMPILER_ARGUMENTS: -Xexplicit-backing-fields
// PROBLEM: none
private val _x = mutableListOf<String>()
var x: List<String>
    get() = _x
    set(value) {}<caret>