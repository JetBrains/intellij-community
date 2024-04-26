// "/(Create member function 'A.foo')|(Create method 'foo' in 'A')/" "true"
// ERROR: Unresolved reference: foo

internal fun test(a: A): Int? {
    return a.foo<String, Int>(1, "2")
}