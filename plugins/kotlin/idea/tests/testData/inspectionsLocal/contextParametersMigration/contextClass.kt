// PROBLEM: none
// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2
// K2_ERROR: Context parameters must be named. Use '_' to declare an anonymous context parameter.
// K2_ERROR: Unsupported [Context parameters on classes are unsupported.].

class C

context(<caret>C)
class MyClass
