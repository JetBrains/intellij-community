// COMPILER_ARGUMENTS: -Xexplicit-backing-fields
// PROBLEM: none
val _x = mutableListOf<String>()
val x: List<String> get() = _x<caret>