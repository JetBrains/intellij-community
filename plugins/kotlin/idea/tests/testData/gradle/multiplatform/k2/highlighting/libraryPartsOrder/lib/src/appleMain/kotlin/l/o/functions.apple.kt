//region Test configuration
// - hidden: line markers
//endregion
package l.o

actual fun <T> nonExpectHasHigherPriority(a: T): RtExpectActual {
    println("appleMain: nonExpectHasHigherPriority (ERROR if used)")
    return RtExpectActual
}

actual fun expectHasHigherPriority(b: Int): RtExpectActual {
    println("appleMain: expectHasHigherPriority (OK if used)")
    return RtExpectActual
}
