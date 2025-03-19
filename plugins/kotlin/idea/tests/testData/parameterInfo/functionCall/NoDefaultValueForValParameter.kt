class A {
    val p = ""

    fun m(a: String = p) {
        m(<caret>)
    }
}

// Text: (<highlight>a: String = p</highlight>), Disabled: false, Strikeout: false, Green: true