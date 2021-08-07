// PROBLEM: Index is always out of bounds
// FIX: none
// WITH_RUNTIME
fun String.removeSuffix(c: Char): String {
    val n = this.length
    if (n > 1) return this.substring(0, n - 2)
    else if (this[n <caret>- 2] == c) return this.substring(0, n - 2)
    else return this
}