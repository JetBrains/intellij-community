package m2

import m1.*

fun testVisibility() {
    PublicClassInM1()

    <error descr="[INVISIBLE_REFERENCE] Cannot access 'class InternalClassInM1 : Any': it is internal in file.">InternalClassInM1</error>()

    <error descr="[INVISIBLE_REFERENCE] Cannot access 'class PrivateClassInM1 : Any': it is private in file.">PrivateClassInM1</error>()

    publicFunInM1()

    <error descr="[INVISIBLE_REFERENCE] Cannot access 'fun internalFunInM1(): Unit': it is internal in file.">internalFunInM1</error>()

    <error descr="[INVISIBLE_REFERENCE] Cannot access 'fun privateFunInM1(): Unit': it is private in file.">privateFunInM1</error>()
}

public class ClassInM2

public class B: <error descr="[INVISIBLE_REFERENCE] Cannot access 'constructor(): A': it is internal in 'm1.A'.">A</error>() {

    fun accessA(a: A) {}

    fun f() {
        <error descr="[INVISIBLE_REFERENCE] Cannot access 'fun pri(): Unit': it is private in 'm1.A'.">pri</error>()

        pro()

        pub()

        <error descr="[INVISIBLE_REFERENCE] Cannot access 'fun int(): Unit': it is internal in 'm1.A'.">int</error>()
    }
}
