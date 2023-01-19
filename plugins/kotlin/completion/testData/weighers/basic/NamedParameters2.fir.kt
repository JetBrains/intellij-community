// FIR_COMPARISON
// order in K1 and K2 differs because of the positions of key words, see KTIJ-23362
fun f(b: Boolean, tra: Int){}

fun test(tri: Boolean, trb: Int) {
    f(tr<caret>)
}

// ORDER: tri
// ORDER: "tra ="
// ORDER: true
// ORDER: try
// ORDER: trb
