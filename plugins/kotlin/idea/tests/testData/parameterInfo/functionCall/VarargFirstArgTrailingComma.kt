fun m(x: Boolean, vararg y: Int) = 2

fun d() {
    m(true,<caret>)
}
/*
Text: (x: Boolean, <highlight>vararg y: Int</highlight>), Disabled: false, Strikeout: false, Green: true
*/