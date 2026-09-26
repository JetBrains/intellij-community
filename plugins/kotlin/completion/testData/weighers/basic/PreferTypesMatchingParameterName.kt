package test

class SomeAUnrelatedClass
class SomeMatchingClass
class SomeZUnrelatedClass

context(someMatchingClass: <caret>)
fun test() {

}


// ORDER: SomeMatchingClass
// ORDER: SomeAUnrelatedClass
// ORDER: SomeZUnrelatedClass
// COMPILER_ARGUMENTS: -Xcontext-parameters