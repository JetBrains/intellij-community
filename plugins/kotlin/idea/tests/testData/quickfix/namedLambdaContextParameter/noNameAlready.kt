// "Remove parameter name" "false"
// COMPILER_ARGUMENTS: -Xcontext-parameters

fun foo(bar: context(<caret>String)() -> Unit) {
}
