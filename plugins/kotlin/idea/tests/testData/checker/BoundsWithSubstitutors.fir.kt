    open class A<T>
    class B<X : A<X>>()

    class C : A<C>()

    val a = B<C>()
    val a1 = B<<error descr="[UPPER_BOUND_VIOLATED]">Int</error>>()

    class X<A, B : A>()

    val b = X<Any, X<A<C>, C>>()
    val b0 = X<Any, <error descr="[UPPER_BOUND_VIOLATED]">Any?</error>>()
    val b1 = X<Any, X<A<C>, <error descr="[UPPER_BOUND_VIOLATED]">String</error>>>()
