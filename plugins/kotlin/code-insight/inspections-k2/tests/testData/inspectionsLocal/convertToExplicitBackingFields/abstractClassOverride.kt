// COMPILER_ARGUMENTS: -Xexplicit-backing-fields
// PROBLEM: none
abstract class Base {
    abstract val data: CharSequence
}

abstract class Derived : Base() {
    private val _data = "internal"
    override val data: CharSequence
        get() = _data<caret>
}