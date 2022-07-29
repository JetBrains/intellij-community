fun f() {
    listOf(1).joinToString(<caret>)
}

/*
Text: (<highlight>separator: CharSequence = ", "</highlight>, prefix: CharSequence = "", postfix: CharSequence = "", limit: Int = -1, truncated: CharSequence = "...", transform: ((Int) -> CharSequence)? = null), Disabled: false, Strikeout: false, Green: true
Text_K2: (<highlight>separator: CharSequence</highlight>, prefix: CharSequence, postfix: CharSequence, limit: Int, truncated: CharSequence, transform: ((Int) -> CharSequence)?), Disabled: false, Strikeout: false, Green: true
 */