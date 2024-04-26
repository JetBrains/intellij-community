//region Test configuration
// - hidden: line markers
//endregion
@file:Suppress("unused_parameter")

package pkg

object RT_GENERIC_JS
object RT_ANY_JS
object RT_INT_JS

actual typealias RT_GENERIC = RT_GENERIC_JS
actual typealias RT_ANY = RT_ANY_JS
actual typealias RT_INT = RT_INT_JS

fun fn2(arg: Int) = RT_INT

actual fun fn3(arg: Int): RT_INT = RT_INT

fun fn4(arg: Any) = RT_ANY

fun fn5(arg: Any) = RT_ANY
fun fn5(arg: Int) = RT_INT

fun fn6(arg: Any) = RT_ANY
actual fun fn6(arg: Int): RT_INT = RT_INT

actual fun fn7(arg: Any): RT_ANY = RT_ANY

actual fun fn8(arg: Any): RT_ANY = RT_ANY
fun fn8(arg: Int) = RT_INT

actual fun fn9(arg: Any): RT_ANY = RT_ANY
actual fun fn9(arg: Int): RT_INT = RT_INT

fun <T> fn10(arg: T) = RT_GENERIC

fun <T> fn11(arg: T) = RT_GENERIC
fun fn11(arg: Int) = RT_INT

fun <T> fn12(arg: T) = RT_GENERIC
actual fun fn12(arg: Int): RT_INT = RT_INT

fun <T> fn13(arg: T) = RT_GENERIC
fun fn13(arg: Any) = RT_ANY

fun <T> fn14(arg: T) = RT_GENERIC
fun fn14(arg: Any) = RT_ANY
fun fn14(arg: Int) = RT_INT

fun <T> fn15(arg: T) = RT_GENERIC
fun fn15(arg: Any) = RT_ANY
actual fun fn15(arg: Int): RT_INT = RT_INT

fun <T> fn16(arg: T) = RT_GENERIC
actual fun fn16(arg: Any): RT_ANY = RT_ANY

fun <T> fn17(arg: T) = RT_GENERIC
actual fun fn17(arg: Any): RT_ANY = RT_ANY
fun fn17(arg: Int) = RT_INT

fun <T> fn18(arg: T) = RT_GENERIC
actual fun fn18(arg: Any): RT_ANY = RT_ANY
actual fun fn18(arg: Int): RT_INT = RT_INT

actual fun <T> fn19(arg: T): RT_GENERIC = RT_GENERIC

actual fun <T> fn20(arg: T): RT_GENERIC = RT_GENERIC
fun fn20(arg: Int) = RT_INT

actual fun <T> fn21(arg: T): RT_GENERIC = RT_GENERIC
actual fun fn21(arg: Int): RT_INT = RT_INT

actual fun <T> fn22(arg: T): RT_GENERIC = RT_GENERIC
fun fn22(arg: Any) = RT_ANY

actual fun <T> fn23(arg: T): RT_GENERIC = RT_GENERIC
fun fn23(arg: Any) = RT_ANY
fun fn23(arg: Int) = RT_INT

actual fun <T> fn24(arg: T): RT_GENERIC = RT_GENERIC
fun fn24(arg: Any) = RT_ANY
actual fun fn24(arg: Int): RT_INT = RT_INT

actual fun <T> fn25(arg: T): RT_GENERIC = RT_GENERIC
actual fun fn25(arg: Any): RT_ANY = RT_ANY

actual fun <T> fn26(arg: T): RT_GENERIC = RT_GENERIC
actual fun fn26(arg: Any): RT_ANY = RT_ANY
fun fn26(arg: Int) = RT_INT

actual fun <T> fn27(arg: T): RT_GENERIC = RT_GENERIC
actual fun fn27(arg: Any): RT_ANY = RT_ANY
actual fun fn27(arg: Int): RT_INT = RT_INT
