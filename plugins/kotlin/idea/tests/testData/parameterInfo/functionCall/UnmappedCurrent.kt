fun m(x: Int, y: Boolean) = 2

fun d() {
    m(y = false, <caret>unmapped = false)
}
/*
Text: (<disabled>[y: Boolean],</disabled><highlight> </highlight>[x: Int]), Disabled: false, Strikeout: false, Green: true
*/