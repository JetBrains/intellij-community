// COMPILER_ARGUMENTS: -Xexplicit-backing-fields
interface I
class C {
    private val _x: MutableList<I> = mutableListOf()
    val x: List<I>
        get() = _x<caret>

    fun update(newX: I) {
        _x.add(newX)
    }
}