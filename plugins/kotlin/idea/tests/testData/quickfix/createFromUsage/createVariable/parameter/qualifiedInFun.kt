// "Create parameter 'foo'" "false"
// ACTION: Create extension property 'A.foo'
// ACTION: Create member property 'A.foo'
// ACTION: Create property 'foo' as constructor parameter
// ACTION: Do not show return expression hints
// ACTION: Rename reference
// ERROR: Unresolved reference: foo

class A

fun test(a: A) {
    val t: Int = a.<caret>foo
}