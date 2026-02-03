//region Test configuration
// - hidden: line markers
//endregion
package srcl.o

fun nonExpectHasHigherPriority(a: Int): RtNonExpectActual {
    println("commonMain: nonExpectHasHigherPriority (OK if used)")
    return RtNonExpectActual
}

expect fun <T> nonExpectHasHigherPriority(a: T): RtExpectActual

fun <T> expectHasHigherPriority(b: T) {
    println("commonMainMain: expectHasHigherPriority (ERROR if used)")
}

expect fun expectHasHigherPriority(b: Int): RtExpectActual

object RtNonExpectActual {
    const val nea = 1
}

object RtExpectActual {
    const val ea = "foo"
}
