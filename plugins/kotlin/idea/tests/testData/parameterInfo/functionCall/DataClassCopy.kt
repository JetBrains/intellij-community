// IGNORE_FIR
data class A(val i: Int, val j: Int)

fun usage(a: A) {
    a.copy(<caret>)
}

//Text: (<highlight>i: Int = ...</highlight>, j: Int = ...), Disabled: false, Strikeout: false, Green: true
