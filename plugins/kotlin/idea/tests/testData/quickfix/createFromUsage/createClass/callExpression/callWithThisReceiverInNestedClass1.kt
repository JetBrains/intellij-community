// "Create class 'Foo'" "true"

class A<T>(val n: T) {
    inner class B<U>(val m: U) {
        fun test() = this.<caret>Foo(2, "2")
    }
}