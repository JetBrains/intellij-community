package Jet87

open class A() {
  fun foo() : Int = 1
}

interface B {
  fun bar() : Double = 1.0;
}

class C() : A(), B

class D() {
  companion object : A(), B {}
}

class Test1<T>()
  where
    T : A,
    T : B,
    <error descr="[NAME_IN_CONSTRAINT_IS_NOT_A_TYPE_PARAMETER]">B</error> : T // error
  {

  fun test(t : T) {
    <error descr="[TYPE_PARAMETER_ON_LHS_OF_DOT]">T</error>.<error descr="[UNRESOLVED_REFERENCE]">foo</error>()
    <error descr="[TYPE_PARAMETER_ON_LHS_OF_DOT]">T</error>.<error descr="[UNRESOLVED_REFERENCE]">bar</error>()
    t.foo()
    t.bar()
  }
}

fun test() {
  Test1<<error descr="[UPPER_BOUND_VIOLATED]">B</error>>()
  Test1<<error descr="[UPPER_BOUND_VIOLATED]">A</error>>()
  Test1<C>()
}

class Foo() {}

class Bar<T : <warning descr="[FINAL_UPPER_BOUND]">Foo</warning>>

class Buzz<T> where T : <warning descr="[FINAL_UPPER_BOUND]">Bar<<error descr="[UPPER_BOUND_VIOLATED]">Int</error>></warning>, T : <error descr="[UNRESOLVED_REFERENCE]">nioho</error>

class X<T : <warning descr="[FINAL_UPPER_BOUND]">Foo</warning>>
class Y<<error descr="[CONFLICTING_UPPER_BOUNDS]">T</error>> where T :  <warning descr="[FINAL_UPPER_BOUND]">Foo</warning>, T : <error descr="[ONLY_ONE_CLASS_BOUND_ALLOWED]"><warning descr="[FINAL_UPPER_BOUND]">Bar<Foo></warning></error>

fun <T> test2(t : T)
  where
    T : A,
    T : B,
    <error descr="[NAME_IN_CONSTRAINT_IS_NOT_A_TYPE_PARAMETER]">B</error> : T
{
  <error descr="[TYPE_PARAMETER_ON_LHS_OF_DOT]">T</error>.<error descr="[UNRESOLVED_REFERENCE]">foo</error>()
  <error descr="[TYPE_PARAMETER_ON_LHS_OF_DOT]">T</error>.<error descr="[UNRESOLVED_REFERENCE]">bar</error>()
  t.foo()
  t.bar()
}

val t1 = test2<<error descr="[UPPER_BOUND_VIOLATED]">A</error>>(<error descr="[TYPE_MISMATCH]">A()</error>)
val t2 = test2<<error descr="[UPPER_BOUND_VIOLATED]">B</error>>(C())
val t3 = test2<C>(C())

val <T, B: T> Pair<T, B>.x : Int get() = 0

class Pair<A, B>()
