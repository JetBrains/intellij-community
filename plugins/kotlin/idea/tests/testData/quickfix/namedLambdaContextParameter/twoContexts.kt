// "Remove parameter name" "true"
// COMPILER_ARGUMENTS: -Xcontext-parameters

fun foo(bar: context(Int, c2: <caret>String)() -> Unit) {
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveParameterNameFix