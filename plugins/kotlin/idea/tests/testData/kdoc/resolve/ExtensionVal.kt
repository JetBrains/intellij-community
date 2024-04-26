package a

class B {
    /**
     * [a.B.<caret>extVal]
     */
    fun member() {
    }
}

val B.extVal: String
    get() = ""

// REF: (a).B.extVal
