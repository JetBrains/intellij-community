fun Foo.test() {
    <lineMarker text="Suspend function call 'foo()'">foo</lineMarker>()
}

class Foo {
    suspend fun foo() {}
}