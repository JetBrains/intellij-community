// WITH_STDLIB
class Task<T> {
    private val tasks: List<Task<T>> = listOf()
    <caret>suspend fun register(f: Task<String>) {
        tasks.forEach {
            it.register(f)
        }
    }
}