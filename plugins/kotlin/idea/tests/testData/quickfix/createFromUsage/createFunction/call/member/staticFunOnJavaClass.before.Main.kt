// "/(Create member function 'J.foo')|(Create method 'foo' in 'J')/" "true"
// ERROR: Unresolved reference: foo

fun test() {
    val a: Int = J.<caret>foo("1", 2)
}

