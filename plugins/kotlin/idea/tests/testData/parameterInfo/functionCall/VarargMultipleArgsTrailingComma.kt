fun m(x: Boolean, vararg y: Int) = 2

fun d() {
    m(true, 1, 2,<caret>)
}
/*
Text: (x: Boolean, <highlight>vararg y: Int</highlight>), Disabled: false, Strikeout: false, Green: true
*/