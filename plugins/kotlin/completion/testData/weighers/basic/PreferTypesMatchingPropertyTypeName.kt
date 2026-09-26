package test

class SomeAUnrelatedClass
class SomeMatchingClass
class SomeZUnrelatedClass

fun test() {
    val someMatchingClass: <caret>
}


// ORDER: SomeMatchingClass
// ORDER: SomeAUnrelatedClass
// ORDER: SomeZUnrelatedClass
// COMPILER_ARGUMENTS: -Xcontext-parameters