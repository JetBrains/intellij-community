fun <T> T.foo(): Function1<@ParameterName("item") T, @ParameterName("item") Unit>

fun f() {
    val v = "a".foo()
    v(<caret>)
}

/*
Text: (<highlight>item: String</highlight>), Disabled: false, Strikeout: false, Green: true
*/
