// WITH_STDLIB
fun javaClassRef() {
    val xKClass = X::class
    val x = xKClass.java
    val y = Y::class.java
    if (<warning descr="Condition 'x === y' is always false">x === y</warning>) {}
}
fun kotlinClassRef() {
    val x = X::class
    val y = Y::class
    if (<warning descr="Condition 'x === y' is always false">x === y</warning>) {}
}
fun notLiteral(x:X, y:Y) {
    if (x::class == y::class) {}
}
open class X
class Y:X()