package test

class SomeAUnrelatedClass
class SomeMatchingClass
class SomeZUnrelatedClass

fun test(someMatchingClass: <caret>) {

}


// ORDER: SomeMatchingClass
// ORDER: SomeAUnrelatedClass
// ORDER: SomeZUnrelatedClass