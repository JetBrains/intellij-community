// "Create class 'Foo'" "true"
// WITH_STDLIB
// ERROR: Unresolved reference: Foo
/* IGNORE_K2 */
open class B

class A<T>(val t: T) {
    val x: B by J.<caret>Foo(t, "")
}
