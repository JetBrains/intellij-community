// COMPILER_ARGUMENTS: -Xexplicit-backing-fields
// PROBLEM: none
interface Base {
    val names: List<String>
}

abstract class AbstractBase : Base {
    private val _names = mutableListOf<String>()
    override val names: List<String> get() = _names<caret>
}