fun foo(n: Int) {
    if (foo > 0) {
        with("foo") {
            <lineMarker text="Recursive call">foo</lineMarker>(n - 1)
        }
    }
}