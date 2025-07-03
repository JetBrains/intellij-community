// "Remove braces" "false"
// TOOL: org.jetbrains.kotlin.idea.codeInsight.inspections.shared.FunctionWithLambdaExpressionBodyInspection
// ACTION: Convert property getter to initializer
// ACTION: Convert to anonymous function
// ACTION: Convert to block body
// ACTION: Convert to multi-line lambda
// ACTION: Enable a trailing comma by default in the formatter
// ACTION: Enable option 'Property types' for 'Types' inlay hints
// ACTION: Introduce local variable
// ACTION: Specify explicit lambda signature
// ACTION: Specify explicit lambda signature
// ACTION: Specify type explicitly
// ACTION: Specify type explicitly
val test get() = <caret>{ "" }

fun foo() {
    test()
}
