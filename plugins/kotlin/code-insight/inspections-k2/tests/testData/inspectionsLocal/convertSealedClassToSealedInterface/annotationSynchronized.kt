// PROBLEM: none
sealed class Task<caret> {
    @Synchronized
    fun run() {
        println("JB")
    }
}
class A : Task()