fun String.foo(a: Int) {}

fun String?.foo(s: String) {}

fun usage(s: String?) {
    s.foo(<caret>)
}
// IGNORE_K1
/*
Text: (<highlight>a: Int</highlight>), Disabled: false, Strikeout: false, Green: false
Text: (<highlight>s: String</highlight>), Disabled: false, Strikeout: false, Green: false
*/
