// "Remove unused destructuring entry" "true"
// TOOL: org.jetbrains.kotlin.idea.codeInsight.inspections.diagnosticBased.UnusedVariableInspection
// COMPILER_ARGUMENTS: -Xname-based-destructuring=complete

data class Foo(val x: String, val y: Int)

fun test(f: (Foo) -> Unit) {}

fun bar() {
    test { (val <caret>x, val y) ->
        y.hashCode()
    }
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.RemoveUnusedVariableFix
