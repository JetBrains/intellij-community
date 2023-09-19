// FIR_COMPARISON

interface A {
    fun foo(): Any
    fun bar()
}
interface B: A {
    override fun foo(): String
    fun baz()
}

fun f(pair: Pair<out A, out Any>) {
    if (pair.first !is B) return
    pair.first.<caret>
}
// EXIST: { lookupString: "foo", "typeText":"Any", attributes: "bold", icon: "nodes/abstractMethod.svg"}
// EXIST: { lookupString: "bar", attributes: "bold", icon: "nodes/abstractMethod.svg"}
// EXIST: { lookupString: "baz", attributes: "grayed", icon: "nodes/abstractMethod.svg"}
