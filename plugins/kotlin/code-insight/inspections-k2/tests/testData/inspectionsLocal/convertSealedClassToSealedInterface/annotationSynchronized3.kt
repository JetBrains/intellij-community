// FIX: Convert to sealed interface
sealed class<caret> Task {
    companion object {
        @Synchronized
        fun run() {
            println("JB")
        }
    }
}
class A : Task()