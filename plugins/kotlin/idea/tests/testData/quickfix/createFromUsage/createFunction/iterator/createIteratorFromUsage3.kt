// "Create member function 'FooIterator.next'" "true"
class FooIterator<T> {
    operator fun hasNext(): Boolean { return false }
}
class Foo<T> {
    operator fun iterator(): FooIterator<String> {
        TODO("not implemented")
    }
}
operator fun Any.component1(): Int {
    return 0
}
operator fun Any.component2(): Int {
    return 0
}
fun foo() {
    for ((i: Int, j: Int) in Foo<caret><Int>()) { }
}