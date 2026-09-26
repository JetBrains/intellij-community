package c

class C<T>: <error descr="[SUPERTYPE_NOT_A_CLASS_OR_INTERFACE]">T</error> by <error descr="[TYPE_MISMATCH]">{
}</error>

class D<T>: <error descr="[SUPERTYPE_NOT_A_CLASS_OR_INTERFACE]">T</error> by<EOLError descr="Expecting an expression"></EOLError>

class G<T> : <error descr="[SUPERTYPE_NOT_A_CLASS_OR_INTERFACE]">T</error> by <error descr="[TYPE_MISMATCH]">{

    val <warning descr="[UNUSED_VARIABLE]">c</warning> = 3
}</error>

interface I

class A<T : I>(a: T) : <error descr="[SUPERTYPE_NOT_A_CLASS_OR_INTERFACE]">T</error> by a