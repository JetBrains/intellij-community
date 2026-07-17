class A : <error descr="[CYCLIC_INHERITANCE_HIERARCHY]">A</error>() {}

val x : Int = <error descr="[INITIALIZER_TYPE_MISMATCH]"><error descr="[TYPE_MISMATCH]">A()</error></error>
