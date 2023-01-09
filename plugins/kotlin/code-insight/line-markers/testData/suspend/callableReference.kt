suspend fun test() {
    val x = <lineMarker text="Suspend function call 'foo()'">foo</lineMarker>()::bar
    <lineMarker text="Suspend operator call 'invoke()'">x</lineMarker>()
}

suspend fun foo(): Foo {
    return Foo()
}

class Foo {
    suspend fun bar() {}
}