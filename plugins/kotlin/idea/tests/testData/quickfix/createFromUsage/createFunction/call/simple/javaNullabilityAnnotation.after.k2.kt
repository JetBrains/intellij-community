// "Create function 'bar'" "true"
// ERROR: Unresolved reference: bar

fun foo(foo: Foo) {
    val s = foo.eval()
    bar(s)
}

fun bar(string: String?) {
    <selection>TODO("Not yet implemented")<caret></selection>
}
