// "Create local variable 'foo'" "false"
// ACTION: Convert property initializer to getter
// ACTION: Convert to lazy property
// ACTION: Create parameter 'foo'
// ACTION: Create property 'foo'
// ACTION: Move to constructor
// ACTION: Rename reference
// ERROR: Unresolved reference: foo

class A {
    val t: Int = <caret>foo
}