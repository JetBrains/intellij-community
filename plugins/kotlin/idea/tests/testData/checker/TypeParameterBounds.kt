// FIR_IDENTICAL
// See KT-9438: Enforce the Single Instantiation Inheritance Rule for type parameters

interface A

interface B

interface D<T>

interface CorrectF<T> where T : D<A>, T : <error descr="[REPEATED_BOUND]">D<B></error>

fun <T> bar() where T : D<A>, T : <error descr="[REPEATED_BOUND]">D<B></error> {}
