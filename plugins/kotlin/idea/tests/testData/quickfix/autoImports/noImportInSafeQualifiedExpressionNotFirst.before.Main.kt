// "Import" "false"
// ERROR: Unresolved reference: SomeTest
// ERROR: Expression expected, but a package name found
// ACTION: Convert property initializer to getter
// ACTION: Convert to lazy property
// ACTION: Create class 'SomeTest'
// ACTION: Rename reference
// ACTION: Replace safe access expression with 'if' expression

package testing

val x = testing?.<caret>SomeTest()
