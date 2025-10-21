package test

class SomeAUnrelatedClass
class SomeMatchingClass
class SomeZUnrelatedClass

fun test() {
    val someMatchingClass: <caret>
}

// IGNORE_K1
// ORDER: SomeMatchingClass
// ORDER: SomeAUnrelatedClass
// ORDER: SomeZUnrelatedClass
// COMPILER_ARGUMENTS: -Xcontext-parameters