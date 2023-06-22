fun f() {
    listOf(1).joinToString(<caret>)
}

/*
Text_K1: Text: (<highlight>separator: CharSequence = ", "</highlight>,
Text_K1: prefix: CharSequence = "",
Text_K1: postfix: CharSequence = "",
Text_K1: limit: Int = -1,
Text_K1: truncated: CharSequence = "...",
Text_K1: transform: ((Int) -> CharSequence)? = null), Disabled: false, Strikeout: false, Green: true
Text_K2: Text: (<highlight>separator: CharSequence</highlight>,
Text_K2: prefix: CharSequence,
Text_K2: postfix: CharSequence,
Text_K2: limit: Int,
Text_K2: truncated: CharSequence,
Text_K2: transform: ((Int) -> CharSequence)?), Disabled: false, Strikeout: false, Green: true
 */