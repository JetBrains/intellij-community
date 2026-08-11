package shared.test

import shared.*

private fun privateInM3Test() {
}
internal fun internalInM3Test() {
}
public fun publicInM3Test() {
}

fun access() {
    <error descr="[INVISIBLE_REFERENCE]">privateInM1</error>()
    <error descr="[INVISIBLE_REFERENCE]">internalInM1</error>()
    publicInM1()

    <error descr="[INVISIBLE_REFERENCE]">privateInM1Test</error>()
    <error descr="[INVISIBLE_REFERENCE]">internalInM1Test</error>()
    publicInM1Test()

    <error descr="[INVISIBLE_REFERENCE]">privateInM2</error>()
    <error descr="[INVISIBLE_REFERENCE]">internalInM2</error>()
    publicInM2()

    <error descr="[INVISIBLE_REFERENCE]">privateInM2Test</error>()
    <error descr="[INVISIBLE_REFERENCE]">internalInM2Test</error>()
    publicInM2Test()

    <error descr="[INVISIBLE_REFERENCE]">privateInM3</error>()
    internalInM3()
    publicInM3()

    privateInM3Test()
    internalInM3Test()
    publicInM3Test()
}
