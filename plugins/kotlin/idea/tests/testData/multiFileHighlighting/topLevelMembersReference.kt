package first

import util.*

fun test() {
    topLevelFun(topLevelVar)
    topLevelFun(topLevelVal)
    val c = C("ff", 1)
    c.s.<error descr="[UNRESOLVED_REFERENCE]">invalid</error>
    val <warning descr="[UNUSED_VARIABLE]">b</warning> = B()
    funWithVararg(1, 2, 3)
    val <warning descr="[UNUSED_VARIABLE]">i</warning> = Invalid()
    topLevelObject.f()
    topLevelObject.g()
 }

fun testWhere(list: List<Int>) {
    funWithWhere(1, list)
    funWithWhere(1, <error descr="[CONSTANT_EXPECTED_TYPE_MISMATCH]">2</error>)
}

class Example : C("foo", 0)
class Example2 : <error descr="[FINAL_SUPERTYPE]">A</error>(2)
