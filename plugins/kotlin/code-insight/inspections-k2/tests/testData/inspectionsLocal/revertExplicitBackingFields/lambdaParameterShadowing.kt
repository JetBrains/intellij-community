// COMPILER_ARGUMENTS: -Xexplicit-backing-fields
interface I

class C {
    val x: List<I>
        field<caret>: MutableList<I> = mutableListOf()

    fun update(newX: I) {
        listOf(1).forEach { _x ->
            println(_x)
            x.add(newX)
        }
    }
}
