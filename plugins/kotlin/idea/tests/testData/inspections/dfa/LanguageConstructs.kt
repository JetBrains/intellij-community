// WITH_STDLIB
fun objectDeclaration() {
    var x : Any = object : X(), Y {}
    if (<warning descr="Condition 'x is X' is always true">x is X</warning>) {}
    if (<warning descr="Condition 'x is Y' is always true">x is Y</warning>) {}
    if (<warning descr="Condition 'x is Z' is always false">x is Z</warning>) {}
    if (<warning descr="Condition 'x is ZZ' is always false">x is ZZ</warning>) {}
    if (<warning descr="Condition 'x is ZZZ' is always false">x is ZZZ</warning>) {}
    if (<warning descr="Condition 'x is Int' is always false">x is Int</warning>) {}

    var x1 : Any = object {}
    var x2 : Any = object {}
    var x3 = x1
    if (<warning descr="Condition 'x1 is Int' is always false">x1 is Int</warning>) {}
    if (<warning descr="Condition 'x1 === x2' is always false">x1 === x2</warning>) {}
    if (<warning descr="Condition 'x1 === x3' is always true">x1 === x3</warning>) {}
    if (<warning descr="Condition 'x2 === x3' is always false">x2 === x3</warning>) {}
}
fun destructuring(a: Int, b: Int) {
    val (c, d) = a to b
    if (c > d) {
        if (<warning descr="Condition 'c < d' is always false">c < d</warning>) { }
    }
}
fun localFunction() {
    var x = 5
    fun foo() {
        x++
    }
    foo()
    if (x == 5) {}
}
open class X {}
interface Y {

}
class Z:X() {}
class ZZ:Y {}
class ZZZ:X(), Y{}