@Deprecated("", level = DeprecationLevel.HIDDEN) fun f(x: Int) = 2

fun d(x: Int) {
    f(<caret>1)
}
// NO_CANDIDATES