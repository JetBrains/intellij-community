// EXPECTED_DUPLICATED_HIGHLIGHTING
  open class A {}
  open class B<T : A>()

  class Pair<A, B>

  abstract class C<T : B<<error descr="[UPPER_BOUND_VIOLATED]">Int</error>>, X : (B<<error descr="[UPPER_BOUND_VIOLATED]"><error descr="[UPPER_BOUND_VIOLATED]">Char</error></error>>) -> Pair<B<<error descr="[UPPER_BOUND_VIOLATED]">Any</error>>, B<A>>>() : B<<error descr="[UPPER_BOUND_VIOLATED]">Any</error>>() { // 2 errors
    val a = B<<error descr="[UPPER_BOUND_VIOLATED]">Char</error>>() // error

    abstract val x : (B<<error descr="[UPPER_BOUND_VIOLATED]"><error descr="[UPPER_BOUND_VIOLATED]">Char</error></error>>) -> B<<error descr="[UPPER_BOUND_VIOLATED]">Any</error>>
  }
