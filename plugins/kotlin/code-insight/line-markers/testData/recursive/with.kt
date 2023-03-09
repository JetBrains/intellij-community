class Foo

fun Foo.foo() {
    with(Foo()) {
        <lineMarker text="Recursive call">foo</lineMarker>()
    }
}