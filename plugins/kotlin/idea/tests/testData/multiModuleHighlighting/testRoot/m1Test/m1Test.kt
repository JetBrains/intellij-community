package shared.test

import shared.*

private fun privateInM1Test() {
}
internal fun internalInM1Test() {
}
public fun publicInM1Test() {
}

fun access() {
    <error descr="[INVISIBLE_REFERENCE]">privateInM1</error>()
    internalInM1()
    publicInM1()

    privateInM1Test()
    internalInM1Test()
    publicInM1Test()

    <error descr="[UNRESOLVED_REFERENCE]">privateInM2</error>()
    <error descr="[UNRESOLVED_REFERENCE]">internalInM2</error>()
    <error descr="[UNRESOLVED_REFERENCE]">publicInM2</error>()

    <error descr="[UNRESOLVED_REFERENCE]">privateInM2Test</error>()
    <error descr="[UNRESOLVED_REFERENCE]">internalInM2Test</error>()
    <error descr="[UNRESOLVED_REFERENCE]">publicInM2Test</error>()

    <error descr="[UNRESOLVED_REFERENCE]">privateInM3</error>()
    <error descr="[UNRESOLVED_REFERENCE]">internalInM3</error>()
    <error descr="[UNRESOLVED_REFERENCE]">publicInM3</error>()

    <error descr="[UNRESOLVED_REFERENCE]">privateInM3Test</error>()
    <error descr="[UNRESOLVED_REFERENCE]">internalInM3Test</error>()
    <error descr="[UNRESOLVED_REFERENCE]">publicInM3Test</error>()
}
