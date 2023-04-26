// FIR_COMPARISON
fun test(fals: Int) {
    val falt = 111
    fal<caret>
}

// ORDER: falt, fals, false
// in K2 the closer the local scope is, the higher priority it has