const val default = ""

fun foo(s: String = default) {
    foo(<caret>)
}

/*
Text: (<highlight>s: String = ""</highlight>), Disabled: false, Strikeout: false, Green: true
*/
