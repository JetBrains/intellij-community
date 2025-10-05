// "Remove braces" "false"
// TOOL: org.jetbrains.kotlin.idea.codeInsight.inspections.shared.FunctionWithLambdaExpressionBodyInspection
// ACTION: Convert to anonymous function
// ACTION: Convert to block body
// ACTION: Convert to run { ... }
// ACTION: Enable a trailing comma by default in the formatter
// ACTION: Enable option 'Function return types' for 'Types' inlay hints
// ACTION: Introduce local variable
// ACTION: Specify explicit lambda signature
// ACTION: Specify explicit lambda signature
// ACTION: Specify return type explicitly
fun test(a: Int, b: Int) = <caret>{
    // comment
    ""
}
