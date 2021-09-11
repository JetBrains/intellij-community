// IGNORE_FIR
// TODO: @Deprecated(HIDDEN) candidates should be as if unresolved
@Deprecated("", level = DeprecationLevel.HIDDEN) fun <T> f(x: Int): T? = null

fun d(x: Int) {
    f<<caret>>(1)
}
// NO_CANDIDATES