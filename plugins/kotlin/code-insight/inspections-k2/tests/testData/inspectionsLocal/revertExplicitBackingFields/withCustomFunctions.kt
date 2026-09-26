// COMPILER_ARGUMENTS: -Xexplicit-backing-fields
interface I
class C {
    val x: List<I>
        field<caret>: MutableList<I> = mutableListOf()

    fun update(newX: I) {
        x.add(newX)
    }
}