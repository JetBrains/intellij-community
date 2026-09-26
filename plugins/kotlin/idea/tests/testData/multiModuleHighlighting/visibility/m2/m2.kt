package m2

import m1.*

fun testVisibility() {
    PublicClassInM1()

    <error descr="[INVISIBLE_REFERENCE]">InternalClassInM1</error>()

    <error descr="[INVISIBLE_REFERENCE]">PrivateClassInM1</error>()

    publicFunInM1()

    <error descr="[INVISIBLE_REFERENCE]">internalFunInM1</error>()

    <error descr="[INVISIBLE_REFERENCE]">privateFunInM1</error>()
}

public class ClassInM2

public class B: <error descr="[INVISIBLE_REFERENCE]">A</error>() {

    fun accessA(a: A) {}

    fun f() {
        <error descr="[INVISIBLE_REFERENCE]">pri</error>()

        pro()

        pub()

        <error descr="[INVISIBLE_REFERENCE]">int</error>()
    }
}
