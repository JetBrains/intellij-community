// PROBLEM: none
sealed class Task<caret> {
    @Deprecated("old")
    @Synchronized
    fun run() {}
}
class A : Task()