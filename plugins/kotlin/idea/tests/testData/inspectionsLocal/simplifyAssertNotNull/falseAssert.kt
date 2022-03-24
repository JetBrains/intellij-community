// PROBLEM: none
// WITH_STDLIB

class C {
    fun assert(b: Boolean) { }

    fun foo(p: Array<String?>) {
        val v = p[0]
        <caret>assert(v != null)
    }
}
