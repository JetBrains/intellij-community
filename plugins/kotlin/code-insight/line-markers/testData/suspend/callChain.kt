suspend fun test() {
    <lineMarker text="Suspend function call 'foo()'">foo</lineMarker>()
        .<lineMarker text="Suspend function call 'bar()'">bar</lineMarker>()
}

suspend fun foo(): Foo {
    return Foo()
}

class Foo {
    suspend fun bar() {}
}