fun Foo.test() {
    <lineMarker text="Suspend function call &apos;foo()&apos;">foo</lineMarker>()
}

class Foo {
    suspend fun foo() {}
}