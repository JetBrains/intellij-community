package a

class B {
    /**
     * [List.<caret>ext]
     */
    fun member() {
    }
}

fun <T : Any> List<T>.ext() {}

// REF: (for List<T> in a).ext()
