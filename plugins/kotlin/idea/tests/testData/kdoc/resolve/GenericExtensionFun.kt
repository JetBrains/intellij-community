package a

class B {
    /**
     * [List.<caret>ext]
     */
    fun member() {
    }
}

fun <T> List<T>.ext() {}

// REF: (for List<T> in a).ext()
