// COMPILER_ARGUMENTS: -Xexplicit-backing-fields
// PROBLEM: none
class A {
    private val _storage: String
        get() = "dynamic_internal_value"

    val data: CharSequence
        get() = _storage<caret>
}