fun main() {
    val outer = Outer()
    <caret>println(outer)
}

private class Outer {
    val publ = 11
    protected val prot = 12
    internal val pint = 13
    private val priv = 14
}

// INVOCATION_COUNT: 1
// EXIST: publ
// EXIST: prot
// EXIST: pint
// EXIST: priv

// IGNORE_K1