class A {
    operator fun component1() = 42
    operator fun component2() = 42
}

fun arrayA(): Array<A> = null!!

fun foo(a: A, <warning descr="[UNUSED_PARAMETER]">c</warning>: Int) {
    val (<warning descr="[NAME_SHADOWING]"><warning descr="[UNUSED_VARIABLE]">a</warning></warning>, <warning descr="[UNUSED_VARIABLE]">b</warning>) = a
    val arr = arrayA()
    for ((<warning descr="[NAME_SHADOWING]"><warning descr="[UNUSED_VARIABLE]">c</warning></warning>, <warning descr="[UNUSED_VARIABLE]">d</warning>) in arr) {
    }
}

fun f(<warning descr="[UNUSED_PARAMETER]">p</warning>: Int): Int {
    val <error descr=""><warning descr="[NAME_SHADOWING]"><warning descr="[UNUSED_VARIABLE]">p</warning></warning></error> = 2
    val <error descr=""><warning descr="[NAME_SHADOWING]">p</warning></error> = 3
    return p
}
