suspend fun test() {
    val x = <lineMarker text="Suspend function call &apos;foo()&apos;">foo</lineMarker>()::bar
    <lineMarker text="Suspend operator call &apos;invoke()&apos;">x</lineMarker>()
}

suspend fun foo(): Foo {
    return Foo()
}

class Foo {
    suspend fun bar() {}
}