fun Any.foo(other: Any) {
    <lineMarker text="Recursive call">foo</lineMarker>(1)
    "".<lineMarker text="Recursive call">foo</lineMarker>(1)
    this.<lineMarker text="Recursive call">foo</lineMarker>(1)
    <lineMarker text="Recursive call">foo(1).foo</lineMarker>(2)

    with(other) {
        <lineMarker text="Recursive call">foo</lineMarker>(other)
        this.<lineMarker text="Recursive call">foo</lineMarker>(other)
        <lineMarker text="Recursive call">foo</lineMarker>(this@foo)
        this@foo.<lineMarker text="Recursive call">foo</lineMarker>(other)
    }
}