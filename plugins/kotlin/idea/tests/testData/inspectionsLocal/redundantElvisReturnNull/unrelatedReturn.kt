// PROBLEM: none
inline fun <T> myRun(action: () -> T): T = action()

fun test(param: String?): String? {
    val result: String = myRun {
        return@myRun param <caret>?: return null
    }

    return result
}