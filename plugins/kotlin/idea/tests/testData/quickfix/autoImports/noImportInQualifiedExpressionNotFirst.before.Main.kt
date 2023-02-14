// "Import" "false"
// ACTION: Convert property initializer to getter
// ACTION: Convert to lazy property
// ACTION: Create class 'SomeTest'
// ACTION: Rename reference
// ERROR: Unresolved reference: SomeTest

package testing

val x = testing.<caret>SomeTest()
