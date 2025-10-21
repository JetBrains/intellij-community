// "Remove parameter name" "false"
// COMPILER_ARGUMENTS: -Xcontext-parameters

context(c: <caret>String)
fun foo(bar: () -> Unit) {
}
