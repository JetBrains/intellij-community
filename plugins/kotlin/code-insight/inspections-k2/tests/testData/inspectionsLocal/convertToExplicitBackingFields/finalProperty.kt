// COMPILER_ARGUMENTS: -Xexplicit-backing-fields
interface Base {
    val names: List<String>
}

abstract class AbstractBase : Base {
    private val _names = mutableListOf<String>()
    final override val names: List<String> get() = _names<caret>
}