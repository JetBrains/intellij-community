class Main {
    val publ = 11
    protected val prot = 12
    internal val pint = 13
    private val priv = 14

    fun foo() {
        <caret>println()
    }
}

fun main() {
    Main().foo()
}

// INVOCATION_COUNT: 1
// EXIST: publ
// EXIST: prot
// EXIST: pint
// EXIST: priv