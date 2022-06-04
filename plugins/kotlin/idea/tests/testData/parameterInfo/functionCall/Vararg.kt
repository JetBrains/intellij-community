fun m(x: Boolean, vararg y: Int) = 2

fun d() {
    m(true, 1, <caret>2)
}
/*
Text: (x: Boolean, <highlight>vararg y: Int</highlight>), Disabled: false, Strikeout: false, Green: true
*/