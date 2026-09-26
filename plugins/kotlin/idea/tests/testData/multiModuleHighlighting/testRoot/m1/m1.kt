package shared

import shared.<error descr="[UNRESOLVED_IMPORT]">test</error>.*

private fun privateInM1() {
}
internal fun internalInM1() {
}
public fun publicInM1() {
}

fun access() {
    privateInM1()
    internalInM1()
    publicInM1()

    <error descr="[UNRESOLVED_REFERENCE]">privateInM1Test</error>()
    <error descr="[UNRESOLVED_REFERENCE]">internalInM1Test</error>()
    <error descr="[UNRESOLVED_REFERENCE]">publicInM1Test</error>()

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