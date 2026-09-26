package shared

import shared.<error descr="[UNRESOLVED_IMPORT]">test</error>.*

private fun privateInM3() {
}
internal fun internalInM3() {
}
public fun publicInM3() {
}

fun access() {
    <error descr="[UNRESOLVED_REFERENCE]">privateInM1</error>()
    <error descr="[UNRESOLVED_REFERENCE]">internalInM1</error>()
    <error descr="[UNRESOLVED_REFERENCE]">publicInM1</error>()

    <error descr="[UNRESOLVED_REFERENCE]">privateInM1Test</error>()
    <error descr="[UNRESOLVED_REFERENCE]">internalInM1Test</error>()
    <error descr="[UNRESOLVED_REFERENCE]">publicInM1Test</error>()

    <error descr="[UNRESOLVED_REFERENCE]">privateInM2</error>()
    <error descr="[UNRESOLVED_REFERENCE]">internalInM2</error>()
    <error descr="[UNRESOLVED_REFERENCE]">publicInM2</error>()

    <error descr="[UNRESOLVED_REFERENCE]">privateInM2Test</error>()
    <error descr="[UNRESOLVED_REFERENCE]">internalInM2Test</error>()
    <error descr="[UNRESOLVED_REFERENCE]">publicInM2Test</error>()

    privateInM3()
    internalInM3()
    publicInM3()

    <error descr="[UNRESOLVED_REFERENCE]">privateInM3Test</error>()
    <error descr="[UNRESOLVED_REFERENCE]">internalInM3Test</error>()
    <error descr="[UNRESOLVED_REFERENCE]">publicInM3Test</error>()
}