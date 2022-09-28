fun foo() {
    with("foo", fun(text: String): String {
        <lineMarker text="Recursive call">foo</lineMarker>()
        return text + text
    })
}