package shared.test

import shared.*

private fun privateInM2Test() {
}
internal fun internalInM2Test() {
}
public fun publicInM2Test() {
}

fun access() {
    <error descr="[INVISIBLE_REFERENCE]">privateInM1</error>()
    <error descr="[INVISIBLE_REFERENCE]">internalInM1</error>()
    publicInM1()

    <error descr="[INVISIBLE_REFERENCE]">privateInM1Test</error>()
    <error descr="[INVISIBLE_REFERENCE]">internalInM1Test</error>()
    publicInM1Test()

    <error descr="[INVISIBLE_REFERENCE]">privateInM2</error>()
    internalInM2()
    publicInM2()

    privateInM2Test()
    internalInM2Test()
    publicInM2Test()

    <error descr="[UNRESOLVED_REFERENCE]">privateInM3</error>()
    <error descr="[UNRESOLVED_REFERENCE]">internalInM3</error>()
    <error descr="[UNRESOLVED_REFERENCE]">publicInM3</error>()

    <error descr="[UNRESOLVED_REFERENCE]">privateInM3Test</error>()
    <error descr="[UNRESOLVED_REFERENCE]">internalInM3Test</error>()
    <error descr="[UNRESOLVED_REFERENCE]">publicInM3Test</error>()
}
