// "Create class 'Foo'" "true"

class B<T>(val t: T) {

}

class A<T>(val b: B<T>) {
    fun test() = B.<caret>Foo<String>(2, "2")
}