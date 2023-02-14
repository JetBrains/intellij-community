fun f() {
    listOf(1).foo<<caret>>
}

fun <T, K> List<T>.foo() {}

//Text: (<highlight>T</highlight>, K), Disabled: false, Strikeout: false, Green: false