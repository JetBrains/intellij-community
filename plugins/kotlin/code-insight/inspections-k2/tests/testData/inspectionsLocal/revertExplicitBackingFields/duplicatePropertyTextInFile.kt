// COMPILER_ARGUMENTS: -Xexplicit-backing-fields
interface I

class First {
    val x: List<I>
        field<caret>: MutableList<I> = mutableListOf()

    fun update(newX: I) {
        x.add(newX)
    }
}

class Second {
    val x: List<I>
        field: MutableList<I> = mutableListOf()

    fun update(newX: I) {
        x.add(newX)
    }
}
