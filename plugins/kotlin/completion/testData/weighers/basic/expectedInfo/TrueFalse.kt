fun ff(bbb: Boolean){}

fun g(bbb: Boolean, ccc: Boolean) {
    ff(<caret>)
}

// IGNORE_K2
// ORDER: bbb
// ORDER: true
// ORDER: false
// ORDER: ccc
