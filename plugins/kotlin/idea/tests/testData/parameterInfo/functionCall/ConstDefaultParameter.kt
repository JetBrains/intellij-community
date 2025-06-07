const val default = ""

fun foo(s: String = default) {
    foo(<caret>)
}
// IGNORE_K1
/*
Text: (<highlight>s: String = default = ""</highlight>), Disabled: false, Strikeout: false, Green: true
*/
