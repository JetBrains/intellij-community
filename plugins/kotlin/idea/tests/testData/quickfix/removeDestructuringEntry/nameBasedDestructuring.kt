// "Remove unused destructuring entry" "true"
// TOOL: org.jetbrains.kotlin.idea.codeInsight.inspections.diagnosticBased.UnusedVariableInspection
// COMPILER_ARGUMENTS: -Xname-based-destructuring=complete

class Foo(val x: String, val y: Int)

fun foo(foo: Foo) {
    (val <caret>x, val y) = foo
    y.hashCode()
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.RemoveUnusedVariableFix