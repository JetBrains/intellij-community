class AbsClass {
    fun op(a123<caret>: String) {}
}

fun main() {
    AbsClass().op("")
}

// IGNORE_K1