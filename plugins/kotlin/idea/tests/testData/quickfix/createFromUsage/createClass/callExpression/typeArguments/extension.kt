// "Create class 'Foo'" "false"
// ERROR: Unresolved reference: Foo
// WITH_STDLIB

class A<T>(val items: List<T>) {
    fun test() = items.<caret>Foo<Int, String>(2, "2")
}