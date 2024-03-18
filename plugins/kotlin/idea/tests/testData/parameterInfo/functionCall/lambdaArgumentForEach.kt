// See: KTIJ-23373

// WITH_STDLIB
fun test() {
    listOf(1, 2, 3).forEach { <caret> }
}

/*
Text_K1: Text: (<highlight>Consumer<in Int!>!</highlight>), Disabled: false, Strikeout: false, Green: false
Text_K1: Text: (<highlight>action: (Int) -> Unit</highlight>), Disabled: false, Strikeout: false, Green: true
Text_K2: Text: (<highlight>action: (Int) -> Unit</highlight>), Disabled: false, Strikeout: false, Green: true
Text_K2: Text: (<highlight>p0: Consumer<in Int!>!</highlight>), Disabled: false, Strikeout: false, Green: false
*/