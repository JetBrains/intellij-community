// KT-303 Stack overflow on a cyclic class hierarchy

open class Foo() : <error descr="[CYCLIC_INHERITANCE_HIERARCHY]">Bar</error>() {
  val a : Int = 1
}

open class Bar() : <error descr="[CYCLIC_INHERITANCE_HIERARCHY]">Foo</error>() {

}

val x : Int = <error descr="[INITIALIZER_TYPE_MISMATCH]"><error descr="[TYPE_MISMATCH]">Foo()</error></error>
