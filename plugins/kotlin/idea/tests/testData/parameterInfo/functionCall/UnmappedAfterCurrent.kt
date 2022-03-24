fun m(x: Int, y: Boolean) = 2

fun d() {
    m(<caret>y = false, unmapped = false)
}
/*
Text: (<highlight>[y: Boolean]</highlight>, [x: Int]), Disabled: false, Strikeout: false, Green: true
*/