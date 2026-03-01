// PROBLEM: none
// K2_ERROR: Type argument for reified type parameter 'T' was inferred to the intersection of ['Comparable<*>' & 'Serializable']. Reification of an intersection type results in the common supertype being used. This may lead to subtle issues and an explicit type argument is encouraged.
class A {
    operator fun get(vararg args: Any) {
        println(args.size)
    }

    private fun println(i: Int) {}
}

fun main() {
    val args = arrayOf("a", 1)
    A().<caret>get(*args)
}
