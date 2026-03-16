// COMPILER_ARGUMENTS: -Xexplicit-backing-fields
// PROBLEM: none

interface I {
    val foo: List<String>
}

interface I2 : I {
    private val _foo: MutableList<String>
        get() = mutableListOf<String>()
    override val foo: List<String>
        get() = _foo<caret>
}
