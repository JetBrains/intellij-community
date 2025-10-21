// FIR_IDENTICAL
// CHECK_SYMBOL_NAMES
// HIGHLIGHTER_ATTRIBUTES_KEY
import org.intellij.lang.annotations.Language

fun String.extWithNoInjections(content: String) {}

fun String.extWithInjectedParam(@Language("kotlin") content: String) {}

fun @receiver:Language("kotlin") String.extWithInjectedReceiver(content: String) {}

fun @receiver:Language("kotlin") String.extWithInjectedReceiverAndParam(@Language("kotlin") content: String) {}

val String.propWithRegularReceiver: String get() = "fun g0() {}"

val @receiver:Language("kotlin") String.propWithInjectedReceiver: String
    get() = ""

fun main() {
    "fun f0() {}".extWithNoInjections("fun f1() {}")
    "fun f2() {}".extWithInjectedParam("fun f3() {}")
    "fun f4() {}".extWithInjectedReceiver("fun f5() {}")
    "fun f6() {}".extWithInjectedReceiverAndParam("fun f7() {}")

    val p0 = "fun f8() {}".propWithRegularReceiver
    val p1 = "fun f9() {}".propWithInjectedReceiver
}
