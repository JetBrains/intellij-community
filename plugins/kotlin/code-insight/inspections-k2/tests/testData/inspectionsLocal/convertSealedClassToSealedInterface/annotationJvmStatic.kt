// FIX: Convert to sealed interface
<caret>sealed class Cache {
    companion object {
        @JvmStatic
        fun create() {
            println("JB")
        }
    }
}