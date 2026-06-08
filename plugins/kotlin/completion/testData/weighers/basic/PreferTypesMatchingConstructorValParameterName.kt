package test

class SomeAUnrelatedClass
class SomeMatchingClass
class SomeZUnrelatedClass

class Test(private val someMatchingClass: <caret>) {

}


// ORDER: SomeMatchingClass
// ORDER: SomeAUnrelatedClass
// ORDER: SomeZUnrelatedClass