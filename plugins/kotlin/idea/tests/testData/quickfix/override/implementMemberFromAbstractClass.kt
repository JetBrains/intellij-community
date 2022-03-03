// "Implement members" "true"
// WITH_STDLIB
abstract class A {
    abstract fun foo()
}

<caret>class B : A() {
}
