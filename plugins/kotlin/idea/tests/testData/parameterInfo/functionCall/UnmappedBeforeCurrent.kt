fun m(x: Int, y: Boolean) = 2

fun d() {
    m(unmapped = 1, <caret>y = false)
}
/*
Text: (<highlight>[y: Boolean]</highlight>, [x: Int]), Disabled: true, Strikeout: false, Green: true
*/