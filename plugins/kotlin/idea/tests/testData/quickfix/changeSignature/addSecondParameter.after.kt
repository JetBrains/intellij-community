// "Change the signature of function 'fooBar'" "true"
// ERROR: The integer literal does not conform to the expected type String!
// ERROR: Too many arguments for public open fun fooBar(str: String!): Unit defined in FooBar

fun foo(foo: FooBar) {
    foo.fooBar(1, "fooB<selection><caret></selection>ar")
}