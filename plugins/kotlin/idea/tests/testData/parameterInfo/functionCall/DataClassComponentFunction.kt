data class A(val i: Int, val j: Int)

fun usage(a: A) {
    a.component2(<caret>)
}

//Text: (<no parameters>), Disabled: false, Strikeout: false, Green: true
