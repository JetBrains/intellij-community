// COMPILER_ARGUMENTS: -Xexplicit-backing-fields
// PROBLEM: none
private var _x = mutableListOf<String>()
var x: List<String>
    get() = _x.toList()
    set(value) { _x = value.toMutableList() }<caret>