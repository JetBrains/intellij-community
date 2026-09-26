// FIR_IDENTICAL
// FIR_COMPARISON

class XXX {
    fun b() {
        listOf("A", "B").filter(::aaa<caret>)
    }
    companion object {
        fun aaac(v: String): Boolean = true
    }
}
fun aaaa(v: String): Boolean = true


// ELEMENT: aaac
