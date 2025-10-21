// WITH_STDLIB

open class A<T1 : Any, T2, T3 : List<T1>> where T2 : Runnable, T2 : Cloneable, T3 : Runnable

val v: A<X, <caret>>

