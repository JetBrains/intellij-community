// DISABLE_ERRORS
fun calculate(): Int {
    return call1()<caret>.call5 {
        doSmth()
    }?.ref6?.call7()
}
