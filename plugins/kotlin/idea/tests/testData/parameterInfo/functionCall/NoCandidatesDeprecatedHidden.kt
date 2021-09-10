// IGNORE_FIR
// TODO: @Deprecated(HIDDEN) candidates should be as if unresolved
@Deprecated("", level = DeprecationLevel.HIDDEN) fun f(x: Int) = 2

fun d(x: Int) {
    f(<caret>1)
}
// NO_CANDIDATES