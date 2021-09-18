// WITH_RUNTIME
fun unrelatedTypes(obj : Any) {
    if (obj is X) {
        if (<warning descr="Condition is always false">obj is Y</warning>) { }
        if (<warning descr="Condition is always true">obj !is Y</warning>) { }
    }
}
fun nullableTypes(obj : Any?) {
    if (obj is X?) {
        if (obj is Y?) {
            if (<warning descr="Condition is always true">obj == null</warning>) { }
        }
    }
}
fun nothing(obj : Any) {
    if (<warning descr="Condition is always false">obj is Nothing</warning>) {}
}
fun testAny(obj : Any) {
    if (obj is Int) {}
    if (obj is String) {}
}
fun reactOnSuppression() {
    val x: Int = 1
    @Suppress("USELESS_IS_CHECK")
    if (x is Int) {}
}
class Cls {
    fun testThis() {
        val obj : Any = this
        if (<warning descr="Condition is always true">obj is Cls</warning>) {}
    }
}
fun exactClass(x2 : X) {
    val x = X()
    if (<warning descr="Condition is always false">x is XX</warning>) { }
    if (x2 is XX) {}
}

open class X() {}
open class XX() : X() {}
class Y {}