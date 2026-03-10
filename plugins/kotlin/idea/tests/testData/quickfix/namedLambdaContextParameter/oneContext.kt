// "Remove parameter name" "true"
// COMPILER_ARGUMENTS: -Xcontext-parameters
// K2_ERROR: Named context parameters in function types are unsupported. Use syntax 'context(Type)' instead.

fun foo(bar: context(c: <caret>String)() -> Unit) {
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveParameterNameFix