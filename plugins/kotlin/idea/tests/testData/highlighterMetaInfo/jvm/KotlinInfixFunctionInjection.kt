// FIR_IDENTICAL
// CHECK_SYMBOL_NAMES
// HIGHLIGHTER_ATTRIBUTES_KEY
import org.intellij.lang.annotations.Language

infix fun String.nonInjected(content: String){}

infix fun String.injected(@Language("kotlin") content: String){}

infix @Language("kotlin") fun String.injectedWithReceiver(@Language("kotlin") content: String){}

fun main() {
    "fun f0() {}" nonInjected "fun f1() {}"
    "fun f2() {}" injected "fun f3() {}"
    "fun f4() {}".injected("fun f5() {}")
    "fun f6() {}" injectedWithReceiver "fun f7() {}"
}