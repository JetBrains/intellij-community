fun f() {
    listOf(1).foo(<caret>)
}

fun <T> List<T>.foo(t: T) {}

//Text: (<highlight>t: Int</highlight>), Disabled: false, Strikeout: false, Green: true