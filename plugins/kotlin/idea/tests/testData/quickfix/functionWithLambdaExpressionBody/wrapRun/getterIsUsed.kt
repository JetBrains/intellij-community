// "Convert to run { ... }" "false"
// TOOL: org.jetbrains.kotlin.idea.inspections.FunctionWithLambdaExpressionBodyInspection
// ACTION: Convert property getter to initializer
// ACTION: Convert to anonymous function
// ACTION: Convert to block body
// ACTION: Convert to multi-line lambda
// ACTION: Enable 'Types' inlay hints
// ACTION: Enable a trailing comma by default in the formatter
// ACTION: Introduce local variable
// ACTION: Specify explicit lambda signature
// ACTION: Specify explicit lambda signature
// ACTION: Specify type explicitly
// ACTION: Specify type explicitly
val test get() = <caret>{ "" }

fun foo() {
    test()
}
