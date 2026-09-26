// COMPILER_ARGUMENTS: -Xexplicit-backing-fields
class A {
    val town: List<String>
        field<caret>: MutableList<String>

    init {
        town = mutableListOf()
    }
}