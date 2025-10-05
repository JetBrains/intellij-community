// "Implement as constructor parameters" "false"
// COMPILER_ARGUMENTS: -Xcontext-parameters
// K2_AFTER_ERROR: Class 'A' is not abstract and does not implement abstract member:<br>context(s: Int) val foo: Int
interface I {
    context(s: Int)
    val foo: Int
}

<caret>class A : I

// IGNORE_K1