// "Import class 'FunctionReference'" "true"
// WITH_STDLIB
// ACTION: Compiler warning 'UNUSED_EXPRESSION' options
// ACTION: Create annotation 'FunctionReference'
// ACTION: Create class 'FunctionReference'
// ACTION: Create enum 'FunctionReference'
// ACTION: Create interface 'FunctionReference'
// ACTION: Create local variable 'FunctionReference'
// ACTION: Create object 'FunctionReference'
// ACTION: Create parameter 'FunctionReference'
// ACTION: Create property 'FunctionReference'
// ACTION: Import class 'FunctionReference'
// ACTION: Introduce local variable
// ACTION: Rename reference

package kotlin

fun some() {
    FunctionReference<caret>::class
}