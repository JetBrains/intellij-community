// "Add function to supertype…" "true"
open class A {
}
open class B : A() {
}
class C : B() {
    <caret>override fun f() {}
}