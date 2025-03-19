// "/(Create member function 'A.foo')|(Create method 'foo' in 'A')/" "true"
// ERROR: Unresolved reference: foo

fun test(): Int {
    return A().<caret>foo<String, Int>(1, "2")
}