// IGNORE_K2

interface Z {
    companion object {
        val instance: Z? = null
    }
}

fun foo(): Z? = Z<caret>

// ORDER: instance
// ORDER: object