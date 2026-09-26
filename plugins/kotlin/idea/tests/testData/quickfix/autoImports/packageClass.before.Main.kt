// "Import" "false"
// ACTION: Create class 'FooPackage'
// ACTION: Create function 'FooPackage'
// ACTION: Rename reference
// ERROR: Unresolved reference: FooPackage

package packageClass

fun functionImportTest() {
    <caret>FooPackage()
}
