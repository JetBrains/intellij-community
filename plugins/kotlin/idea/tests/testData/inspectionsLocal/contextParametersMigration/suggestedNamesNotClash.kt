// "Replace context receivers with context parameters" "true"
// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2
// K2_ERROR: Context parameters must be named. Use '_' to declare an anonymous context parameter.

class FooBarBaz

context(<caret>FooBarBaz)
fun test() {}

fun nonClash1(baz: FooBarBaz) {}

context(baz: FooBarBaz)
fun nonClash2() {}

fun baz() {}
