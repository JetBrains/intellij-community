interface A {
    fun foo() {}
}
interface B : A, <error descr="[CYCLIC_INHERITANCE_HIERARCHY]">E</error> {}
interface C : <error descr="[CYCLIC_INHERITANCE_HIERARCHY]">B</error> {}
interface D : <error descr="[CYCLIC_INHERITANCE_HIERARCHY]">B</error> {}
interface E : <error descr="[CYCLIC_INHERITANCE_HIERARCHY]">F</error> {}
interface F : <error descr="[CYCLIC_INHERITANCE_HIERARCHY]">D</error>, <error descr="[CYCLIC_INHERITANCE_HIERARCHY]">C</error> {}
interface G : F {}
interface H : F {}

val a : A? = null
val b : B? = null
val c : C? = null
val d : D? = null
val e : E? = null
val f : F? = null
val g : G? = null
val h : H? = null

fun test() {
    a?.foo()
    b?.foo()
    c?.<error descr="[UNRESOLVED_REFERENCE]">foo</error>()
    d?.<error descr="[UNRESOLVED_REFERENCE]">foo</error>()
    e?.<error descr="[UNRESOLVED_REFERENCE]">foo</error>()
    f?.<error descr="[UNRESOLVED_REFERENCE]">foo</error>()
    g?.<error descr="[UNRESOLVED_REFERENCE]">foo</error>()
    h?.<error descr="[UNRESOLVED_REFERENCE]">foo</error>()
}
