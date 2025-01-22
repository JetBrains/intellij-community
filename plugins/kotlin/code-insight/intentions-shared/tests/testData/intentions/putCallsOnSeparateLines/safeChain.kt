// DISABLE_ERRORS
fun calculate(): Int {
    return call1()?.call2()?.call3()?<caret>.call5()?.ref6?.call7()
}
