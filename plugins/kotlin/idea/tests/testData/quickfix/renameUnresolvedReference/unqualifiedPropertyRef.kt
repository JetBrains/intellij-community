// "Rename reference" "true"
// ERROR: Unresolved reference: x
// ERROR: Unresolved reference: x
class A {
    val a = 1
    val s = ""

    fun bar(i: Int) {

    }

    fun baz(i: Int) {

    }

    fun foo() {
        bar(<caret>x)
        baz(x)
        bar(x())
        baz(x(1))
    }
}