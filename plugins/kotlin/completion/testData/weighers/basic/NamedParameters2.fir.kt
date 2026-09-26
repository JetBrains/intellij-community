// FIR_COMPARISON
fun f(b: Boolean, tra: Int){}

fun test(tri: Boolean, trb: Int) {
    f(tr<caret>)
}

// ORDER: tri
// ORDER: true
// ORDER: "tra ="
// ORDER: try
// ORDER: trb
