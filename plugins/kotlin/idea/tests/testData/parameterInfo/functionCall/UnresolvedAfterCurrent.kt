fun m(x: Int, y: Boolean) = 2

fun d() {
    m(1, <caret>unresolved)
}
/*
Text: (x: Int, <highlight>y: Boolean</highlight>), Disabled: false, Strikeout: false, Green: true
*/