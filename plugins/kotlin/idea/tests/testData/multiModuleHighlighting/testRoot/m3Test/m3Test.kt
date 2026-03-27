package shared.test

import shared.*

private fun privateInM3Test() {
}
internal fun internalInM3Test() {
}
public fun publicInM3Test() {
}

fun access() {
    <error descr="[INVISIBLE_REFERENCE] Cannot access 'fun privateInM1(): Unit': it is private in file.">privateInM1</error>()
    <error descr="[INVISIBLE_REFERENCE] Cannot access 'fun internalInM1(): Unit': it is internal in file.">internalInM1</error>()
    publicInM1()

    <error descr="[INVISIBLE_REFERENCE] Cannot access 'fun privateInM1Test(): Unit': it is private in file.">privateInM1Test</error>()
    <error descr="[INVISIBLE_REFERENCE] Cannot access 'fun internalInM1Test(): Unit': it is internal in file.">internalInM1Test</error>()
    publicInM1Test()

    <error descr="[INVISIBLE_REFERENCE] Cannot access 'fun privateInM2(): Unit': it is private in file.">privateInM2</error>()
    <error descr="[INVISIBLE_REFERENCE] Cannot access 'fun internalInM2(): Unit': it is internal in file.">internalInM2</error>()
    publicInM2()

    <error descr="[INVISIBLE_REFERENCE] Cannot access 'fun privateInM2Test(): Unit': it is private in file.">privateInM2Test</error>()
    <error descr="[INVISIBLE_REFERENCE] Cannot access 'fun internalInM2Test(): Unit': it is internal in file.">internalInM2Test</error>()
    publicInM2Test()

    <error descr="[INVISIBLE_REFERENCE] Cannot access 'fun privateInM3(): Unit': it is private in file.">privateInM3</error>()
    internalInM3()
    publicInM3()

    privateInM3Test()
    internalInM3Test()
    publicInM3Test()
}
