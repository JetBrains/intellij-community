// "Create property 'foo' as constructor parameter" "false"
// ERROR: Unresolved reference: foo
// K2_AFTER_ERROR: Unresolved reference 'foo'.

class A {
    object B {
        fun test(): Int {
            return <caret>foo
        }
    }
}
