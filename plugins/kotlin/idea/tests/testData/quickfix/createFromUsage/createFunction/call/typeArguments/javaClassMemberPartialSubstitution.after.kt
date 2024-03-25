// "/(Create member function 'B.foo')|(Create method 'foo' in 'B')/" "true"
// ERROR: Unresolved reference: foo
// ERROR: 'public' property exposes its 'public/*package*/' type B

class A<T> internal constructor(val b: B<T>) {
    fun test(): Int {
        return b.foo<Int>(2, "2")
    }
}