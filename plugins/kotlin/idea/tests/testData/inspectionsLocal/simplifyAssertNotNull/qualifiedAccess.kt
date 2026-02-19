// PROBLEM: none
// WITH_STDLIB

class C(val v: String?) {
    fun foo(p: Array<String?>) {
        val v = p[0]
        <caret>assert(this.v != null)
    }
}
