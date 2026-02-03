// PROBLEM: none
fun bar(x: String?) = ""

class A(private var a: String?) {
    fun foo() {
        <caret>if (a != null) bar(a) else null
    }
}
