// FIR_COMPARISON
// FIR_IDENTICAL
val initGlobal = 12

fun test(initParam : Int) {
    val initLocal = "Test"
    init<caret>
}

// ORDER: initLocal, initParam, initGlobal