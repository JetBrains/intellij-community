// "Rename reference" "true"
// ERROR: Unresolved reference: x
// ERROR: Unresolved reference: x
// ERROR: Unresolved reference: x
// ERROR: Unresolved reference: x
class A {
    fun f() = 1
    fun g() = ""

    fun bar(i: Int) {

    }

    fun baz(i: Int) {

    }

    fun foo() {
        bar(x)
        baz(x)
        bar(<caret>x())
        baz(x())
        bar(x(1))
        baz(x(1))
    }
}