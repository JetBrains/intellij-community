// FIR_IDENTICAL
// FIR_COMPARISON
// COMPILER_ARGUMENTS: -XXLanguage:-NewInference

interface A {
    fun foo(): Any
    fun bar()
}
interface B: A {
    override fun foo(): String
}

fun f(pair: Pair<out A, out Any>) {
    if (pair.first !is B) return
    pair.first.<caret>
}
// EXIST: { lookupString: "foo", "typeText":"Any", attributes: "bold" }
// EXIST: { lookupString: "bar", attributes: "bold" }
