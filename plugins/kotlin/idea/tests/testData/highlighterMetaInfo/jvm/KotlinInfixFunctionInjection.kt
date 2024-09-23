// FIR_IDENTICAL
// CHECK_SYMBOL_NAMES
// HIGHLIGHTER_ATTRIBUTES_KEY
import org.intellij.lang.annotations.Language

infix fun String.xml(@Language("kotlin") content: String){}

infix @Language("kotlin") fun String.xml2(@Language("kotlin") content: String){}

fun main() {
    "foo" xml "fun foo() {}"
    "foo".xml("fun foo() {}")
    "fun bar() {}" xml2 "fun foo() {}"
}