// WITH_STDLIB
fun testUnit(<warning descr="[UNUSED_PARAMETER] Parameter 'x' is never used">x</warning>: Int) {

}

fun check(a1: MySingleton, a2: MySingleton, a3: MySingleton?) {
    if (<warning descr="Condition 'testUnit(1) == testUnit(2)' is always true">testUnit(1) == testUnit(2)</warning>) {
    }
    if (<warning descr="Condition 'a1 != a2' is always false">a1 != a2</warning>) {}
    if (a1 != a3) {
        if (<warning descr="Condition 'a3 == null' is always true">a3 == null</warning>) {}
    }
}

object MySingleton