package shared

import shared.<error descr="[UNRESOLVED_IMPORT]">test</error>.*

private fun privateInM2() {
}
internal fun internalInM2() {
}
public fun publicInM2() {
}

fun access() {
    <error descr="[INVISIBLE_REFERENCE]">privateInM1</error>()
    <error descr="[INVISIBLE_REFERENCE]">internalInM1</error>()
    publicInM1()

    <error descr="[UNRESOLVED_REFERENCE]">privateInM1Test</error>()
    <error descr="[UNRESOLVED_REFERENCE]">internalInM1Test</error>()
    <error descr="[UNRESOLVED_REFERENCE]">publicInM1Test</error>()

    privateInM2()
    internalInM2()
    publicInM2()

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
