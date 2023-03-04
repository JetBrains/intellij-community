// PROBLEM: none

interface MyInterface {
    class Nested
}

class Foo : MyInterface {
    fun test(p: MyInterface<caret>.Nested) {}
}
