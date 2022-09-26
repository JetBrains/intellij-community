// FIR_IDENTICAL
// FIR_COMPARISON
fun usage() {
    fun myLocalFun() {}

    myLocalFu<caret>
}

// ELEMENT: myLocalFun