// "Create class 'Foo'" "true"
// WITH_STDLIB
// ERROR: Unresolved reference: Foo

open class B

class A<T>(val t: T) {
    val x: B by J.Foo(t, "")
}
