// "Implement as constructor parameters" "false"
// K2_AFTER_ERROR: Class 'A' is not abstract and does not implement abstract member:<br>val Int.foo: Int
interface I {
    val Int.foo: Int
}

<caret>class A : I

// IGNORE_K1