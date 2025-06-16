fun String.foo(a: Int) {}

fun usage(s: String?) {
    s.foo(<caret>)
}
/*
Text: (<highlight>a: Int</highlight>), Disabled: false, Strikeout: false, Green: true
*/
