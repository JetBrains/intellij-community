// COMPILER_ARGUMENTS: -Xexplicit-backing-fields
class A {
    private val _town: MutableList<String>
    val town: List<String>
        get() = _town<caret>

    init {
        _town = mutableListOf()
    }
}