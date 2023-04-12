fun foo(n: Int) {
    inlineBlock { <lineMarker text="Recursive call">foo</lineMarker>(n - 1) }
    simpleBlock { <lineMarker text="Recursive call">foo</lineMarker>(n - 2) }
    noInlineBlock { <lineMarker text="Recursive call">foo</lineMarker>(n - 3) }
}

fun foo2(n: Int) {
    val d = { <lineMarker text="Recursive call">foo</lineMarker>(n - 1) }
    { <lineMarker text="Recursive call">foo</lineMarker>(1) }
    { <lineMarker text="Recursive call">foo</lineMarker>(n + 4) }();
    { <lineMarker text="Recursive call">foo</lineMarker>(n/2) }.invoke()
}

inline fun inlineBlock(block: () -> Unit) = block()
fun simpleBlock(block: () -> Unit) = block()
inline fun noInlineBlock(noinline block: () -> Unit) = block()