// "Create property 'foo'" "true"
// ERROR: Property must be initialized or be abstract

class A {
    object B {
        fun test(): Int {
            return <caret>foo
        }
    }
}
