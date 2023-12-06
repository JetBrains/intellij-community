// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: "operator fun minus(Diction): Diction"

open class Diction {
    operator fun <caret>minus(other: Diction): Diction = Diction()
    operator fun plus(other: Diction): Diction = Diction()
}

operator fun Diction.times(other: Diction) = Diction()

class A

operator fun A.div(other: A) = Diction()

fun indirectDiction() = A() / A()
fun indirectPlusDiction() = indirectDiction() + indirectDiction()

val t = { d: Diction -> d + d }
val tt = { t(indirectDiction()) }

fun test1(d1: Diction, d2: Diction) {
    val a = d1 + d2
    val b = d1 - d2
    val c = d1 * d2
    val d = b - c
}

fun test2() {
    val dInT2 = indirectDiction()
    dInT2 - dInT2
}

fun test3() {
    val dInT3 = indirectPlusDiction()
    dInT3 - dInT3
}

fun test4() {
    tt() - tt()
}


// IGNORE_K2_LOG