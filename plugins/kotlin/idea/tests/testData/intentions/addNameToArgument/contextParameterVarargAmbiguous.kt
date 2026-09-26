// COMPILER_ARGUMENTS: -Xcontext-parameters -XXLanguage:+ExplicitContextArguments
// IS_APPLICABLE: false
// ERROR: Context parameters are not supported in K1 mode. Consider using a more recent language version and switching to K2 mode.
// ERROR: Unresolved reference: with
// ERROR: Unresolved reference: with
// K2_ERROR:

context(x: String, y: Int)
fun foo(vararg items: String): String = ""

fun main() {
    with("ctx") {
        with(42) {
            foo(<caret>"a", "b", "c")
        }
    }
}