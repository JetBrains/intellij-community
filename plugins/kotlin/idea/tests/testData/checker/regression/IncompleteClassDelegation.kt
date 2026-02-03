package c

class C<T>: <error descr="[SUPERTYPE_NOT_A_CLASS_OR_INTERFACE] Only classes and interfaces may serve as supertypes">T</error> by <error descr="[TYPE_MISMATCH] Type mismatch: inferred type is () -> Unit but T was expected">{
}</error>

class D<T>: <error descr="[SUPERTYPE_NOT_A_CLASS_OR_INTERFACE] Only classes and interfaces may serve as supertypes">T</error> by<EOLError descr="Expecting an expression"></EOLError>

class G<T> : <error descr="[SUPERTYPE_NOT_A_CLASS_OR_INTERFACE] Only classes and interfaces may serve as supertypes">T</error> by <error descr="[TYPE_MISMATCH] Type mismatch: inferred type is () -> Unit but T was expected">{

    val <warning descr="[UNUSED_VARIABLE] Variable 'c' is never used">c</warning> = 3
}</error>

interface I

class A<T : I>(a: T) : <error descr="[SUPERTYPE_NOT_A_CLASS_OR_INTERFACE] Only classes and interfaces may serve as supertypes">T</error> by a