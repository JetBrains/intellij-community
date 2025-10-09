package test

class SomeAUnrelatedClass
class SomeMatchingClass
class SomeZUnrelatedClass

context(someMatchingClass: <caret>)
fun test() {

}

// IGNORE_K1
// ORDER: SomeMatchingClass
// ORDER: SomeAUnrelatedClass
// ORDER: SomeZUnrelatedClass
// COMPILER_ARGUMENTS: -Xcontext-parameters