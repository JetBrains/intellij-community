suspend fun test() {
    <lineMarker text="Suspend function call &apos;foo()&apos;">foo</lineMarker>()
        .<lineMarker text="Suspend function call &apos;bar()&apos;">bar</lineMarker>()
}

suspend fun foo(): Foo {
    return Foo()
}

class Foo {
    suspend fun bar() {}
}