fun foo() {
    with("foo", fun(text: String): String {
        <lineMarker text="Recursive call">foo</lineMarker>()
        return text + text
    })
}

fun foo2(i: Int) {
    val d = fun(i: Int) { <lineMarker text="Recursive call">foo2</lineMarker>(i) }
    val d1 = fun(i: Int) { <lineMarker text="Recursive call">foo2</lineMarker>(i) }.invoke(5)
}