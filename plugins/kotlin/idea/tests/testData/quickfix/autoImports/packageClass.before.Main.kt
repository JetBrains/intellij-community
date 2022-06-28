// "Import" "false"
// ACTION: Create class 'FooPackage'
// ACTION: Create function 'FooPackage'
// ACTION: Do not show return expression hints
// ACTION: Rename reference
// ERROR: Unresolved reference: FooPackage

package packageClass

fun functionImportTest() {
    <caret>FooPackage()
}
