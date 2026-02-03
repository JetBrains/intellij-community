package a

class B {
    /**
     * [a.B.<caret>ext]
     */
    fun member() {
    }
}

fun B.ext() {
}

// REF: (a).B.ext()
