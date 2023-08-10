fun m(x: Boolean, vararg y: Int) = 2

fun d() {
    m(true, <caret>)
}

// TYPE: "true, "

/*
Text: (x: Boolean, <highlight>vararg y: Int</highlight>), Disabled: true, Strikeout: false, Green: true
*/