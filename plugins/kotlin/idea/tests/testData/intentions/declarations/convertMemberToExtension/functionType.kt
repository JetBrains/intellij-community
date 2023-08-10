// AFTER-WARNING: Parameter 'p' is never used
class Owner {
    fun <caret>f(p: () -> Unit): (Int) -> String {
        return { it.toString() }
    }
}
