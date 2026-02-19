// "Add parameter to function 'fooBar'" "true"
// ERROR: Too many arguments for public open fun fooBar(string: String!): Unit defined in FooBar

fun foo(foo: FooBar) {
    foo.fooBar("first", "fooB<caret>ar")
}