fun m(x: Boolean, vararg y: Int) = 2

fun d() {
    val a = intArrayOf(1, 2, 3)
    m(true, 1, *a<caret>, 4)
}
/*
Text: (x: Boolean, <highlight>vararg y: Int</highlight>), Disabled: false, Strikeout: false, Green: true
*/