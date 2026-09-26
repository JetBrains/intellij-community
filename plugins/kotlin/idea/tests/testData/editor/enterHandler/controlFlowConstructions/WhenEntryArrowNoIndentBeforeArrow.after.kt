fun a() {
    when (true) {
        false -> Unit
        true
        <caret>-> Unit
    }
}

// SET_FALSE: INDENT_BEFORE_ARROW_ON_NEW_LINE
