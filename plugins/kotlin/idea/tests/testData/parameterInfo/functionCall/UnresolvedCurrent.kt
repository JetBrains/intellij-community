fun m(x: Int, y: Boolean) = 2

fun d() {
    m(<caret>1, unresolved)
}
/*
Text: (<highlight>x: Int</highlight>, y: Boolean), Disabled: false, Strikeout: false, Green: true
*/