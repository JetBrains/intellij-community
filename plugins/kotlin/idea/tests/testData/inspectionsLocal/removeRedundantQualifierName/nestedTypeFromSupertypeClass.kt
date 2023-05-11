open class MyBaseClass {
    class Nested
}

class Foo : MyBaseClass() {
    fun test(p: MyBaseClass<caret>.Nested) {}
}
