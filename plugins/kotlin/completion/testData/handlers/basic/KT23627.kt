// FIR_IDENTICAL
// FIR_COMPARISON

fun foo() = "".run {
    this.apply {
        printl<caret>
    }
}
//ELEMENT: println
//TAIL_TEXT: "() (kotlin.io)"
