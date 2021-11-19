// WITH_STDLIB
class X {
    var p: Int = 0

    fun test(x: X) {
        if (x.p > p) {
            if (<warning descr="Condition 'x.p > p' is always true">x.p > p</warning>) {
            }
        }
        p = 1
        x.p = 2
        if (<warning descr="Condition 'p < x.p' is always true">p < x.p</warning>) {}
        <warning descr="Value of 'x.p -= 2' is always zero">x.p -= 2</warning>
        if (<warning descr="Condition 'p < x.p' is always false">p < <weak_warning descr="Value of 'x.p' is always zero">x.p</weak_warning></warning>) {}
    }
}
fun dataClass(x : Data) {
    if (x.x == 0) return
    x.x++
    if (<warning descr="Condition 'x.x == 1' is always false">x.x == 1</warning>) return
}
fun nullableProperty(d : Data) = d.x1 == null || d.x1 < 0

data class Data(var x: Int, val x1: Int?)

fun arrayQualifier(arr : Array<X>, i: Int, j: Int) {
    if (arr[i].p == arr[j].p) {}
}
class ClassWithParameter(private val x:Boolean) {
    fun test(other : ClassWithParameter) {
        if (x) {

        } else if (!other.x) {

        }
    }
}
data class Point(val x: Int, val y: Int) {
    fun test(p: Point) {
        if (p.y < p.x && p.x < x && x < y) {
            if (<warning descr="Condition 'p.y < y' is always true">p.y < y</warning>) {

            }
        }
    }
}
data class MutableData(var x: Int) {
    fun test() {
        val xx = x
        update()
        if (xx == x) {}
    }

    fun update() {
        x++
    }
}

fun safeNullCheck(x: X, kls: Kls?) {
    if (x == kls?.x) return
    if (kls != null) {
        if (<warning descr="Condition 'x != kls.x' is always true">x != kls.x</warning>) {}
    }
}

class Kls(val x: X)
