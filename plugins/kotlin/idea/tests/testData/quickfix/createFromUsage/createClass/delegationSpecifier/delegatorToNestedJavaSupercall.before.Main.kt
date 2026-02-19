// "Create class 'A'" "true"
// ERROR: Unresolved reference: A
// IGNORE_K2
class B {

}

class Foo: J.<caret>A(1, "2", B()) {

}