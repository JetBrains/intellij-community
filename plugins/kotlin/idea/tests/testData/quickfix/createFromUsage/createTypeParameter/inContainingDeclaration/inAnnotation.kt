// "Create type parameter 'Test' in class 'C'" "false"
// ERROR: Unresolved reference: Test
class C {
    @<caret>Test fun foo() {

    }
}