// PROBLEM: none
// IGNORE_K1
// IGNORE_K2
inline fun <T> myRun(action: () -> T): T = action()

fun test(param: String?): String? {
    val result: String = myRun {
        return@myRun param <caret>?: return null
    }

    return result
}