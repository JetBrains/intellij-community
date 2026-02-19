//region Test configuration
// - hidden: line markers
//endregion
@file:Suppress("unused_parameter")

package pkg

expect class RT_GENERIC
expect class RT_ANY
expect class RT_INT

fun <T> fn1(arg: T): RT_GENERIC = null!!
fun fn1(arg: Any): RT_ANY = null!!
fun fn1(arg: Int): RT_INT = null!!

fun <T> fn2(arg: T): RT_GENERIC = null!!
fun fn2(arg: Any): RT_ANY = null!!

fun <T> fn3(arg: T): RT_GENERIC = null!!
fun fn3(arg: Any): RT_ANY = null!!
expect fun fn3(arg: Int): RT_INT

fun <T> fn4(arg: T): RT_GENERIC = null!!
fun fn4(arg: Int): RT_INT = null!!

fun <T> fn5(arg: T): RT_GENERIC = null!!

fun <T> fn6(arg: T): RT_GENERIC = null!!
expect fun fn6(arg: Int): RT_INT

fun <T> fn7(arg: T): RT_GENERIC = null!!
expect fun fn7(arg: Any): RT_ANY
fun fn7(arg: Int): RT_INT = null!!

fun <T> fn8(arg: T): RT_GENERIC = null!!
expect fun fn8(arg: Any): RT_ANY

fun <T> fn9(arg: T): RT_GENERIC = null!!
expect fun fn9(arg: Any): RT_ANY
expect fun fn9(arg: Int): RT_INT

fun fn10(arg: Any): RT_ANY = null!!
fun fn10(arg: Int): RT_INT = null!!

fun fn11(arg: Any): RT_ANY = null!!

fun fn12(arg: Any): RT_ANY = null!!
expect fun fn12(arg: Int): RT_INT

fun fn13(arg: Int): RT_INT = null!!

expect fun fn15(arg: Int): RT_INT

expect fun fn16(arg: Any): RT_ANY
fun fn16(arg: Int): RT_INT = null!!

expect fun fn17(arg: Any): RT_ANY

expect fun fn18(arg: Any): RT_ANY
expect fun fn18(arg: Int): RT_INT

expect fun <T> fn19(arg: T): RT_GENERIC
fun fn19(arg: Any): RT_ANY = null!!
fun fn19(arg: Int): RT_INT = null!!

expect fun <T> fn20(arg: T): RT_GENERIC
fun fn20(arg: Any): RT_ANY = null!!

expect fun <T> fn21(arg: T): RT_GENERIC
fun fn21(arg: Any): RT_ANY = null!!
expect fun fn21(arg: Int): RT_INT

expect fun <T> fn22(arg: T): RT_GENERIC
fun fn22(arg: Int): RT_INT = null!!

expect fun <T> fn23(arg: T): RT_GENERIC

expect fun <T> fn24(arg: T): RT_GENERIC
expect fun fn24(arg: Int): RT_INT

expect fun <T> fn25(arg: T): RT_GENERIC
expect fun fn25(arg: Any): RT_ANY
fun fn25(arg: Int): RT_INT = null!!

expect fun <T> fn26(arg: T): RT_GENERIC
expect fun fn26(arg: Any): RT_ANY

expect fun <T> fn27(arg: T): RT_GENERIC
expect fun fn27(arg: Any): RT_ANY
expect fun fn27(arg: Int): RT_INT
