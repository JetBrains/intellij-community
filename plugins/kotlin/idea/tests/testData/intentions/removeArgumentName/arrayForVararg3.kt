// AFTER-WARNING: Parameter 's' is never used
import kotlin.arrayOf as fooBar

fun foo(vararg s: String) {}

fun test() {
    foo(<caret>s = fooBar("", ""))
}