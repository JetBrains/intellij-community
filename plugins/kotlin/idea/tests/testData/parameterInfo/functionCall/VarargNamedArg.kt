fun m(x: Boolean, vararg y: Int) = 2

fun d() {
    val a = intArrayOf(1, 2, 3)
    m(y = <caret>a, x = true)
}
/*
Text: (<highlight>[vararg y: Int]</highlight>, [x: Boolean]), Disabled: false, Strikeout: false, Green: true
*/