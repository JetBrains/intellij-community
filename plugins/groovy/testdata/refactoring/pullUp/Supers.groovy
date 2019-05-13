class SuperClass {
    def foo() {}
}
class SuubClass extends SuperClass {
    def ba<caret>r() {
        super.foo()
    }
}