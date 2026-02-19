// COMPILER_ARGUMENTS: -Xexplicit-backing-fields

interface I {
    val foo: List<String>
}

class C : I {
    private val _foo = mutableListOf<String>()

    override val foo: List<String>
        get() = _foo<caret>
}