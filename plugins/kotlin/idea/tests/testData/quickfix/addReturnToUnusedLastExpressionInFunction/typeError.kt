// "Add 'return' before the expression" "false"
// WITH_STDLIB
// ACTION: Add full qualifier
// ACTION: Compiler warning 'UNUSED_EXPRESSION' options
// ACTION: Introduce import alias
// ACTION: Introduce local variable
// ERROR: A 'return' expression required in a function with a block body ('{...}')
// ERROR: Unresolved reference: FunctionReference

fun some(): FunctionReference {
    Int<caret>::class
}
