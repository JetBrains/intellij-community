// "Create local variable 'foo'" "true"
// ACTION: Create local variable 'foo'
// ACTION: Create property 'foo'
// ACTION: Create property 'foo' as constructor parameter
// ACTION: Rename reference

class A {
    val t: Int get() {
        return <caret>foo
    }
}