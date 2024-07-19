//region Test configuration
// - hidden: line markers
//endregion
package srcl.o

actual fun <T> nonExpectHasHigherPriority(a: T): RtExpectActual {
    println("linuxMain: nonExpectHasHigherPriority (ERROR if used)")
    return RtExpectActual
}

actual fun expectHasHigherPriority(b: Int): RtExpectActual {
    println("linuxMain: expectHasHigherPriority (OK if used)")
    return RtExpectActual
}
