// "Remove parameter name" "true"
// COMPILER_ARGUMENTS: -Xcontext-parameters
// K2_ERROR: Named context parameters in function types are unsupported. Use syntax 'context(Type)' instead.

fun foo(bar: context(/*1*/c/*2*/:/*3*/ <caret>String/*4*/)() -> Unit) {
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveParameterNameFix