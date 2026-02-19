//region Test configuration
// - hidden: line markers
//endregion
package pkg

/**
 *```
 * C - one declaration in common code
 * P - one declaration in platform code
 * E - expect and actual declarations
 * ____|<T>|Any|Int
 * fn1 | C | C | C
 * fn2 | C | C | P
 * fn3 | C | C | E
 * fn4 | C | P | C
 * fn5 | C | P | P
 * fn6 | C | P | E
 * fn7 | C | E | C
 * fn8 | C | E | P
 * fn9 | C | E | E
 * fn10| P | C | C
 * fn11| P | C | P
 * fn12| P | C | E
 * fn13| P | P | C
 * fn14| P | P | P
 * fn15| P | P | E
 * fn16| P | E | C
 * fn17| P | E | P
 * fn18| P | E | E
 * fn19| E | C | C
 * fn20| E | C | P
 * fn21| E | C | E
 * fn22| E | P | C
 * fn23| E | P | P
 * fn24| E | P | E
 * fn25| E | E | C
 * fn26| E | E | P
 * fn27| E | E | E
 * ```
 */
@Suppress("unused_variable", "unused")
fun test() {
    val fn1r1: RT_GENERIC_JVM = fn1<Unit>(Unit)
    val fn1r2: RT_ANY_JVM = fn1(Unit)
    val fn1r3: RT_INT_JVM = fn1(0)

    val fn2r1: RT_GENERIC_JVM = fn2<Unit>(Unit)
    val fn2r2: RT_ANY_JVM = fn2(Unit)
    val fn2r3: RT_INT_JVM = fn2(0)

    val fn3r1: RT_GENERIC_JVM = fn3<Unit>(Unit)
    val fn3r2: RT_ANY_JVM = fn3(Unit)
    val fn3r3: RT_INT_JVM = fn3(0)

    val fn4r1: RT_GENERIC_JVM = fn4<Unit>(Unit)
    val fn4r2: RT_ANY_JVM = fn4(Unit)
    val fn4r3: RT_INT_JVM = fn4(0)

    val fn5r1: RT_GENERIC_JVM = fn5<Unit>(Unit)
    val fn5r2: RT_ANY_JVM = fn5(Unit)
    val fn5r3: RT_INT_JVM = fn5(0)

    val fn6r1: RT_GENERIC_JVM = fn6<Unit>(Unit)
    val fn6r2: RT_ANY_JVM = fn6(Unit)
    val fn6r3: RT_INT_JVM = fn6(0)

    val fn7r1: RT_GENERIC_JVM = fn7<Unit>(Unit)
    val fn7r2: RT_ANY_JVM = fn7(Unit)
    val fn7r3: RT_INT_JVM = fn7(0)

    val fn8r1: RT_GENERIC_JVM = fn8<Unit>(Unit)
    val fn8r2: RT_ANY_JVM = fn8(Unit)
    val fn8r3: RT_INT_JVM = fn8(0)

    val fn9r1: RT_GENERIC_JVM = fn9<Unit>(Unit)
    val fn9r2: RT_ANY_JVM = fn9(Unit)
    val fn9r3: RT_INT_JVM = fn9(0)

    val fn10r1: RT_GENERIC_JVM = fn10<Unit>(Unit)
    val fn10r2: RT_ANY_JVM = fn10(Unit)
    val fn10r3: RT_INT_JVM = fn10(0)

    val fn11r1: RT_GENERIC_JVM = fn11<Unit>(Unit)
    val fn11r2: RT_ANY_JVM = fn11(Unit)
    val fn11r3: RT_INT_JVM = fn11(0)

    val fn12r1: RT_GENERIC_JVM = fn12<Unit>(Unit)
    val fn12r2: RT_ANY_JVM = fn12(Unit)
    val fn12r3: RT_INT_JVM = fn12(0)

    val fn13r1: RT_GENERIC_JVM = fn13<Unit>(Unit)
    val fn13r2: RT_ANY_JVM = fn13(Unit)
    val fn13r3: RT_INT_JVM = fn13(0)

    val fn14r1: RT_GENERIC_JVM = fn14<Unit>(Unit)
    val fn14r2: RT_ANY_JVM = fn14(Unit)
    val fn14r3: RT_INT_JVM = fn14(0)

    val fn15r1: RT_GENERIC_JVM = fn15<Unit>(Unit)
    val fn15r2: RT_ANY_JVM = fn15(Unit)
    val fn15r3: RT_INT_JVM = fn15(0)

    val fn16r1: RT_GENERIC_JVM = fn16<Unit>(Unit)
    val fn16r2: RT_ANY_JVM = fn16(Unit)
    val fn16r3: RT_INT_JVM = fn16(0)

    val fn17r1: RT_GENERIC_JVM = fn17<Unit>(Unit)
    val fn17r2: RT_ANY_JVM = fn17(Unit)
    val fn17r3: RT_INT_JVM = fn17(0)

    val fn18r1: RT_GENERIC_JVM = fn18<Unit>(Unit)
    val fn18r2: RT_ANY_JVM = fn18(Unit)
    val fn18r3: RT_INT_JVM = fn18(0)

    val fn19r1: RT_GENERIC_JVM = fn19<Unit>(Unit)
    val fn19r2: RT_ANY_JVM = fn19(Unit)
    val fn19r3: RT_INT_JVM = fn19(0)

    val fn20r1: RT_GENERIC_JVM = fn20<Unit>(Unit)
    val fn20r2: RT_ANY_JVM = fn20(Unit)
    val fn20r3: RT_INT_JVM = fn20(0)

    val fn21r1: RT_GENERIC_JVM = fn21<Unit>(Unit)
    val fn21r2: RT_ANY_JVM = fn21(Unit)
    val fn21r3: RT_INT_JVM = fn21(0)

    val fn22r1: RT_GENERIC_JVM = fn22<Unit>(Unit)
    val fn22r2: RT_ANY_JVM = fn22(Unit)
    val fn22r3: RT_INT_JVM = fn22(0)

    val fn23r1: RT_GENERIC_JVM = fn23<Unit>(Unit)
    val fn23r2: RT_ANY_JVM = fn23(Unit)
    val fn23r3: RT_INT_JVM = fn23(0)

    val fn24r1: RT_GENERIC_JVM = fn24<Unit>(Unit)
    val fn24r2: RT_ANY_JVM = fn24(Unit)
    val fn24r3: RT_INT_JVM = fn24(0)

    val fn25r1: RT_GENERIC_JVM = fn25<Unit>(Unit)
    val fn25r2: RT_ANY_JVM = fn25(Unit)
    val fn25r3: RT_INT_JVM = fn25(0)

    val fn26r1: RT_GENERIC_JVM = fn26<Unit>(Unit)
    val fn26r2: RT_ANY_JVM = fn26(Unit)
    val fn26r3: RT_INT_JVM = fn26(0)

    val fn27r1: RT_GENERIC_JVM = fn27<Unit>(Unit)
    val fn27r2: RT_ANY_JVM = fn27(Unit)
    val fn27r3: RT_INT_JVM = fn27(0)
}
