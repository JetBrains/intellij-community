// "Rename reference" "true"
// ERROR: Unresolved reference: x
// ERROR: Unresolved reference: x
// ERROR: Unresolved reference: x
// ERROR: Unresolved reference: x
class A {
    val a = 1
    val s = ""
}

class B

fun bar(i: Int) {

}

fun baz(i: Int) {

}

fun foo(a: A, b: B) {
    val aa = A()
    bar(a.x<caret>)
    baz(a.x)
    bar(a.x())
    baz(a.x(1))
    bar(aa.x)
    bar(b.x)
}