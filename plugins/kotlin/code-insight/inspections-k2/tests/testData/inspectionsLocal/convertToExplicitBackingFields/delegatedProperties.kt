// COMPILER_ARGUMENTS: -Xexplicit-backing-fields
// PROBLEM: none
class Example {
    private val _p: String = "Hello"
    val p: String by this::_p<caret>
}