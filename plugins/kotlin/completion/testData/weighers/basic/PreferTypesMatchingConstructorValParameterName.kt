package test

class SomeAUnrelatedClass
class SomeMatchingClass
class SomeZUnrelatedClass

class Test(private val someMatchingClass: <caret>) {

}

// IGNORE_K1
// ORDER: SomeMatchingClass
// ORDER: SomeAUnrelatedClass
// ORDER: SomeZUnrelatedClass