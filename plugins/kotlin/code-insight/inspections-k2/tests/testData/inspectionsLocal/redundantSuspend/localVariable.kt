// PROBLEM: none
suspend<caret> fun test(action: suspend () -> String) {
    val local = action()
}

