// "Import" "false"
// ACTION: Convert property initializer to getter
// ACTION: Convert to lazy property
// ACTION: Create class 'SomeTest'
// ACTION: Do not show return expression hints
// ACTION: Rename reference
// ERROR: Unresolved reference: SomeTest

package testing

val x = testing.<caret>SomeTest()
