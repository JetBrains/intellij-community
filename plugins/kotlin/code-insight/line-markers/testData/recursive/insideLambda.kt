fun foo(n: Int) {
    inlineBlock { <lineMarker text="Recursive call">foo</lineMarker>(n - 1) }
    simpleBlock { <lineMarker text="Recursive call">foo</lineMarker>(n - 2) }
    noInlineBlock { <lineMarker text="Recursive call">foo</lineMarker>(n - 3) }
}

inline fun inlineBlock(block: () -> Unit) = block()
fun simpleBlock(block: () -> Unit) = block()
inline fun noInlineBlock(noinline block: () -> Unit) = block()