// WITH_RUNTIME
fun destructuring(a: Int, b: Int) {
    val (c, d) = a to b
    if (c > d) {
        if (<warning descr="Condition is always false">c < d</warning>) { }
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
fun objectDeclaration() {
    var x : Any = object : X(), Y {}
    if (<warning descr="Condition is always true">x is X</warning>) {}
    if (<warning descr="Condition is always true">x is Y</warning>) {}
    if (<warning descr="Condition is always false">x is Z</warning>) {}
    if (<warning descr="Condition is always false">x is ZZ</warning>) {}
    if (x is ZZZ) {}
    if (<warning descr="Condition is always false">x is Int</warning>) {}
}
open class X {}
interface Y {

}
class Z:X() {}
class ZZ:Y {}
class ZZZ:X(), Y{}