@Deprecated("", level = DeprecationLevel.HIDDEN) fun f(x: Int) = 2
fun f(x: Int, y: Boolean) = 3

fun d(x: Int) {
    f(<caret>1)
}
/*
Text: (<highlight>x: Int</highlight>, y: Boolean), Disabled: false, Strikeout: false, Green: true
*/