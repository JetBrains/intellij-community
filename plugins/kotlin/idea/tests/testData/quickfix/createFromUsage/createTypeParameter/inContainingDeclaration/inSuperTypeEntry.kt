// "Create type parameter 'Test' in class 'C'" "false"
// ACTION: Create class 'Test'
// ACTION: Create test
// ACTION: Do not show return expression hints
// ERROR: Unresolved reference: Test
class C : <caret>Test() {

}