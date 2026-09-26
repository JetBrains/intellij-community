package test

class SomeAUnrelatedClass
class SomeMatchingClass
class SomeZUnrelatedClass

class Test(someMatchingClass: <caret>) {

}


// ORDER: SomeMatchingClass
// ORDER: SomeAUnrelatedClass
// ORDER: SomeZUnrelatedClass